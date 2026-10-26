package com.omni.common.event;

/**
 * Topic names and event types shared by producers and consumers.
 */
public final class Events {

    private Events() {
    }

    public static final String TOPIC_ORDER = "order.events";
    public static final String TOPIC_PAYMENT = "payment.events";

    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_CONFIRMED = "OrderConfirmed";
    public static final String ORDER_CANCELLED = "OrderCancelled";
    public static final String ORDER_STATUS_CHANGED = "OrderStatusChanged";

    public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String PAYMENT_REFUNDED = "PaymentRefunded";
}
