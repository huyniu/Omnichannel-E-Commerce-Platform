package com.omni.order.service;

import com.omni.order.config.OrderProperties;
import com.omni.order.domain.Order;
import com.omni.order.domain.OrderStatus;
import com.omni.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Releases stock held by online orders that were never paid, so a flash-sale item is not locked
 * forever by abandoned checkouts.
 */
@Component
@RequiredArgsConstructor
public class PaymentTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentTimeoutJob.class);

    private final OrderRepository orders;
    private final OrderService orderService;
    private final OrderProperties props;

    @Scheduled(fixedDelayString = "${order.timeout-scan-interval-ms:30000}")
    public void cancelExpired() {
        Instant before = Instant.now().minus(props.paymentTimeout());
        List<UUID> expired = orders.findExpiredIds(OrderStatus.PENDING, Order.PaymentMethod.COD, before, PageRequest.of(0, 100));
        for (UUID id : expired) {
            try {
                orderService.cancelPending(null, id, "Payment timeout", "system");
            } catch (Exception e) {
                log.warn("Could not expire order {}: {}", id, e.getMessage());
            }
        }
        if (!expired.isEmpty()) {
            log.info("Expired {} unpaid orders", expired.size());
        }
    }
}
