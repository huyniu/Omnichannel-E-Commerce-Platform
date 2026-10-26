package com.omni.order.inventory;

import com.omni.common.exception.BusinessException;
import com.omni.order.config.OrderProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.RedissonMultiLock;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed lock per SKU (Redisson, Redlock-style) plus a "sold out" fast-path flag.
 *
 * <p>The lock serializes competing buyers of the same SKU in Redis instead of letting hundreds of
 * requests pile up on the same PostgreSQL row lock and exhaust the connection pool. Correctness does
 * NOT depend on it: the conditional UPDATE in the database is the final guard, so if Redis is
 * unavailable we degrade to database-only locking rather than refusing orders.
 */
@Service
public class InventoryLockService {

    private static final Logger log = LoggerFactory.getLogger(InventoryLockService.class);
    private static final String LOCK_PREFIX = "lock:inventory:";
    private static final String SOLD_OUT_PREFIX = "inventory:soldout:";

    private final RedissonClient redisson;
    private final OrderProperties props;
    private final Counter lockTimeouts;
    private final Counter redisFallbacks;

    public InventoryLockService(RedissonClient redisson, OrderProperties props, MeterRegistry registry) {
        this.redisson = redisson;
        this.props = props;
        this.lockTimeouts = Counter.builder("inventory.lock.timeouts").register(registry);
        this.redisFallbacks = Counter.builder("inventory.lock.redis_fallbacks").register(registry);
    }

    /**
     * Runs {@code action} while holding the locks of all given SKUs. Locks are acquired in sorted
     * order (MultiLock) so two orders containing the same SKUs can never deadlock each other.
     */
    public <T> T withSkuLocks(Collection<String> skus, Supplier<T> action) {
        if (skus.isEmpty()) {
            return action.get();
        }
        RLock[] locks = skus.stream().sorted().distinct().map(s -> redisson.getLock(LOCK_PREFIX + s)).toArray(RLock[]::new);
        RedissonMultiLock multiLock = new RedissonMultiLock(locks);
        boolean acquired;
        try {
            acquired = multiLock.tryLock(props.lockWait().toMillis(), props.lockLease().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("INTERRUPTED", "Request interrupted", 503);
        } catch (RuntimeException redisDown) {
            redisFallbacks.increment();
            log.warn("Redis lock unavailable ({}), falling back to database row locking", redisDown.getMessage());
            return action.get();
        }
        if (!acquired) {
            lockTimeouts.increment();
            throw new BusinessException("INVENTORY_BUSY", "Too many concurrent orders for this product, please retry", 429);
        }
        try {
            return action.get();
        } finally {
            try {
                multiLock.unlock();
            } catch (RuntimeException e) {
                // Lease expired before we finished: the DB guard already kept stock consistent.
                log.warn("Failed to release inventory lock: {}", e.getMessage());
            }
        }
    }

    public boolean anySoldOut(Collection<String> skus) {
        try {
            for (String sku : skus) {
                if (redisson.getBucket(SOLD_OUT_PREFIX + sku).isExists()) {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            log.debug("Sold-out flag check skipped: {}", e.getMessage());
        }
        return false;
    }

    /** Must be called while holding the SKU lock, after the stock change has been committed. */
    public void refreshSoldOutFlags(Map<String, Integer> availableBySku) {
        try {
            availableBySku.forEach((sku, available) -> {
                RBucket<String> flag = redisson.getBucket(SOLD_OUT_PREFIX + sku);
                if (available <= 0) {
                    flag.set("1", Duration.ofMinutes(10));
                } else {
                    flag.delete();
                }
            });
        } catch (RuntimeException e) {
            log.debug("Sold-out flag update skipped: {}", e.getMessage());
        }
    }
}
