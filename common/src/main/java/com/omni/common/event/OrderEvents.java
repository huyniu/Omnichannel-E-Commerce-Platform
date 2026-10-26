package com.omni.common.event;

import java.time.Instant;
import java.util.List;

/**
 * Payloads published by the Order &amp; Inventory Service on {@link Events#TOPIC_ORDER}.
 */
public final class OrderEvents {

    private OrderEvents() {
    }

    public record Item(String sku, String productName, long unitPrice, int quantity) {
    }

    public record OrderCreated(String orderId, String orderCode, Long userId, String customerEmail,
                               long totalAmount, String paymentMethod, List<Item> items, Instant createdAt) {
    }

    public record OrderConfirmed(String orderId, String orderCode, Long userId, String customerEmail, long totalAmount) {
    }

    /**
     * @param refundRequired true when the order had already been paid, so Payment must refund it.
     */
    public record OrderCancelled(String orderId, String orderCode, Long userId, String customerEmail,
                                 String reason, boolean refundRequired) {
    }

    public record OrderStatusChanged(String orderId, String orderCode, Long userId, String customerEmail,
                                     String oldStatus, String newStatus) {
    }
}
