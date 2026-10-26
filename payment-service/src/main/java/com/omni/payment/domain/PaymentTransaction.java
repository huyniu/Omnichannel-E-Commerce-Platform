package com.omni.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
public class PaymentTransaction {

    public enum Type { CHECKOUT, CHARGE, REFUND, COLLECT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private long amount;

    @Column(name = "raw_payload")
    private String rawPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static PaymentTransaction of(Payment p, Type type, String status, String raw) {
        PaymentTransaction t = new PaymentTransaction();
        t.setPaymentId(p.getId());
        t.setType(type.name());
        t.setStatus(status);
        t.setAmount(p.getAmount());
        t.setRawPayload(raw);
        return t;
    }
}
