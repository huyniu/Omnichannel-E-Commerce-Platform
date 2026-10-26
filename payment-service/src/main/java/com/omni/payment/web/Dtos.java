package com.omni.payment.web;

import com.omni.payment.domain.Payment;
import com.omni.payment.domain.PaymentTransaction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class Dtos {

    private Dtos() {
    }

    public record CheckoutResponse(String paymentId, String orderId, String provider, long amount, String paymentUrl) {
    }

    public record PaymentResponse(String id, String orderId, String orderCode, long amount, String method,
                                  String provider, String status, String providerTxnId, String failureReason,
                                  Instant paidAt, Instant refundedAt, Instant createdAt) {
        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId().toString(), p.getOrderId().toString(), p.getOrderCode(),
                    p.getAmount(), p.getMethod().name(), p.getProvider(), p.getStatus().name(), p.getProviderTxnId(),
                    p.getFailureReason(), p.getPaidAt(), p.getRefundedAt(), p.getCreatedAt());
        }
    }

    /** Body the mock gateway POSTs to our webhook. */
    public record MockWebhook(@NotBlank String txnRef, @NotBlank String status, @NotNull Long amount,
                              String providerTxnId, @NotBlank String signature) {
    }

    public record TransactionResponse(String type, String status, long amount, Instant createdAt) {
        public static TransactionResponse from(PaymentTransaction t) {
            return new TransactionResponse(t.getType(), t.getStatus(), t.getAmount(), t.getCreatedAt());
        }
    }

    public record ReconciliationLine(String method, String status, long count, long total) {
    }
}
