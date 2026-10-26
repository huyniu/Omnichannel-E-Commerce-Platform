package com.omni.order.service;

import com.omni.common.exception.BusinessException;
import com.omni.common.web.CurrentUser;
import com.omni.order.config.OrderProperties;
import com.omni.order.client.ProductClient;
import com.omni.order.domain.Order;
import com.omni.order.domain.OrderStatus;
import com.omni.order.inventory.InventoryLockService;
import com.omni.order.inventory.InventoryService;
import com.omni.order.repository.OrderRepository;
import com.omni.order.service.OrderTxService.StockChange;
import com.omni.order.web.Dtos.OrderLine;
import com.omni.order.web.Dtos.OrderResponse;
import com.omni.order.web.Dtos.PlaceOrderRequest;
import com.omni.proto.product.SkuInfo;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Entry point of the order use cases. Orchestrates: gRPC product snapshot -> Redis SKU lock ->
 * local ACID transaction -> sold-out flag refresh -> unlock.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final OrderTxService tx;
    private final InventoryService inventory;
    private final InventoryLockService locks;
    private final ProductClient productClient;
    private final OrderProperties props;
    private final Counter ordersCreated;
    private final Counter outOfStock;
    private final Timer placeOrderTimer;

    public OrderService(OrderRepository orders, OrderTxService tx, InventoryService inventory,
                        InventoryLockService locks, ProductClient productClient, OrderProperties props,
                        MeterRegistry registry) {
        this.orders = orders;
        this.tx = tx;
        this.inventory = inventory;
        this.locks = locks;
        this.productClient = productClient;
        this.props = props;
        this.ordersCreated = Counter.builder("orders.created").register(registry);
        this.outOfStock = Counter.builder("orders.rejected").tag("reason", "out_of_stock").register(registry);
        this.placeOrderTimer = Timer.builder("orders.place.duration").publishPercentiles(0.5, 0.95, 0.99).register(registry);
    }

    public OrderResponse place(CurrentUser user, PlaceOrderRequest req, String idempotencyKey) {
        String key = StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : null;
        if (key != null) {
            Optional<OrderResponse> existing = findByIdempotencyKey(user.id(), key);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Map<String, Integer> quantities = mergeLines(req.items());
        if (locks.anySoldOut(quantities.keySet())) {
            outOfStock.increment();
            throw BusinessException.conflict("OUT_OF_STOCK", "One or more products are sold out");
        }

        Map<String, SkuInfo> snapshots = productClient.getSkus(quantities.keySet());
        for (String sku : quantities.keySet()) {
            SkuInfo info = snapshots.get(sku);
            if (info == null || !info.getActive()) {
                throw BusinessException.badRequest("INVALID_SKU", "Product " + sku + " is not available for sale");
            }
        }

        return placeOrderTimer.record(() -> {
            try {
                StockChange change = locks.withSkuLocks(quantities.keySet(), () -> {
                    StockChange c = tx.create(user, req, quantities, snapshots, key);
                    locks.refreshSoldOutFlags(c.availability());
                    return c;
                });
                ordersCreated.increment();
                return change.order();
            } catch (BusinessException e) {
                if ("OUT_OF_STOCK".equals(e.getCode())) {
                    outOfStock.increment();
                }
                throw e;
            } catch (DataIntegrityViolationException e) {
                // Same idempotency key submitted twice concurrently: return the order that won.
                if (key != null) {
                    return findByIdempotencyKey(user.id(), key).orElseThrow(() -> e);
                }
                throw e;
            }
        });
    }

    public OrderResponse cancelByCustomer(CurrentUser user, UUID orderId, String reason) {
        Order order = orders.findById(orderId).orElseThrow(() -> BusinessException.notFound("Order"));
        if (!order.getUserId().equals(user.id()) && !user.isStaff()) {
            throw BusinessException.notFound("Order");
        }
        String why = StringUtils.hasText(reason) ? reason : "Cancelled by " + (user.isStaff() ? "staff" : "customer");
        return compensate(null, orderId, why, "user:" + user.id(), false)
                .orElseThrow(() -> BusinessException.conflict("CANNOT_CANCEL", "Order cannot be cancelled"));
    }

    /** Payment failed / timed out: cancel only if still waiting for payment. */
    public void cancelPending(String eventId, UUID orderId, String reason, String actor) {
        compensate(eventId, orderId, reason, actor, true);
    }

    private Optional<OrderResponse> compensate(String eventId, UUID orderId, String reason, String actor, boolean onlyIfPending) {
        List<String> skus = inventory.skusOf(orderId);
        return locks.withSkuLocks(skus, () -> {
            Optional<StockChange> change = tx.cancel(eventId, orderId, reason, actor, onlyIfPending);
            change.ifPresent(c -> locks.refreshSoldOutFlags(c.availability()));
            return change.map(StockChange::order);
        });
    }

    public OrderResponse updateStatus(UUID orderId, OrderStatus status, String reason, CurrentUser staff) {
        return tx.updateStatus(orderId, status, reason, "user:" + staff.id());
    }

    @Transactional(readOnly = true)
    public OrderResponse get(CurrentUser user, UUID orderId) {
        Order order = orders.findWithItemsById(orderId).orElseThrow(() -> BusinessException.notFound("Order"));
        if (!order.getUserId().equals(user.id()) && !user.isStaff()) {
            throw BusinessException.notFound("Order");
        }
        return OrderResponse.from(order, true);
    }

    @Transactional(readOnly = true)
    public Page<Order> listMine(Long userId, Pageable pageable) {
        return orders.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Order> listAll(OrderStatus status, Pageable pageable) {
        return status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable);
    }

    private Optional<OrderResponse> findByIdempotencyKey(Long userId, String key) {
        // Goes through the OrderTxService proxy: a self-call here would run without a transaction.
        return tx.findByIdempotencyKey(userId, key);
    }

    private Map<String, Integer> mergeLines(List<OrderLine> lines) {
        Map<String, Integer> merged = new TreeMap<>();
        for (OrderLine line : lines) {
            merged.merge(line.sku().trim().toUpperCase(), line.quantity(), Integer::sum);
        }
        merged.forEach((sku, qty) -> {
            if (qty > props.maxQuantityPerSku()) {
                throw BusinessException.badRequest("QUANTITY_LIMIT",
                        "Maximum " + props.maxQuantityPerSku() + " units per product");
            }
        });
        return merged;
    }
}
