package com.omni.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    public enum Method { COD, VNPAY, MOCK }

    /**
     * PENDING: waiting for the customer to pay online. AWAITING_COLLECTION: COD, cash collected on delivery.
     * SUCCEEDED / FAILED: final gateway outcome. CANCELLED: order cancelled before payment. REFUNDED.
     */
    public enum Status { PENDING, AWAITING_COLLECTION, SUCCEEDED, FAILED, CANCELLED, REFUNDED }

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "order_code", nullable = false)
    private String orderCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Method method;

    @Column(nullable = false)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "txn_ref", nullable = false, unique = true)
    private String txnRef;

    @Column(name = "provider_txn_id")
    private String providerTxnId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public boolean isFinal() {
        return status == Status.SUCCEEDED || status == Status.FAILED || status == Status.CANCELLED
                || status == Status.REFUNDED;
    }
}
