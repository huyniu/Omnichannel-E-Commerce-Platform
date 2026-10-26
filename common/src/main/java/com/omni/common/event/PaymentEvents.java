package com.omni.common.event;

/**
 * Payloads published by the Payment &amp; Billing Service on {@link Events#TOPIC_PAYMENT}.
 */
public final class PaymentEvents {

    private PaymentEvents() {
    }

    /**
     * Shared by PaymentSucceeded, PaymentFailed and PaymentRefunded.
     */
    public record PaymentResult(String paymentId, String orderId, String orderCode, Long userId, String customerEmail,
                                long amount, String method, String provider, String status, String reason) {
    }
}
