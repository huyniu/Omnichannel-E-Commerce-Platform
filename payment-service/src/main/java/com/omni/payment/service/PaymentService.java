package com.omni.payment.service;

import com.omni.common.event.Events;
import com.omni.common.event.OrderEvents;
import com.omni.common.event.PaymentEvents.PaymentResult;
import com.omni.common.exception.BusinessException;
import com.omni.common.web.CurrentUser;
import com.omni.payment.domain.Payment;
import com.omni.payment.domain.PaymentTransaction;
import com.omni.payment.domain.ProcessedEvent;
import com.omni.payment.messaging.OutboxWriter;
import com.omni.payment.provider.GatewayResult;
import com.omni.payment.provider.MockGateway;
import com.omni.payment.provider.VnPayGateway;
import com.omni.payment.repository.PaymentRepository;
import com.omni.payment.repository.PaymentTransactionRepository;
import com.omni.payment.repository.ProcessedEventRepository;
import com.omni.payment.web.Dtos.CheckoutResponse;
import com.omni.payment.web.Dtos.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Outcome of applying a gateway callback; mapped to the response code the gateway expects. */
    public enum CallbackOutcome { APPLIED, ALREADY_PROCESSED, NOT_FOUND, AMOUNT_MISMATCH }

    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final ProcessedEventRepository processedEvents;
    private final OutboxWriter outbox;
    private final MockGateway mockGateway;
    private final VnPayGateway vnPayGateway;

    // ---------- Saga: react to order events ----------

    /** OrderCreated: open a payment for the order (one per order, enforced by a unique constraint). */
    @Transactional
    public void onOrderCreated(String eventId, OrderEvents.OrderCreated order) {
        if (alreadyProcessed(eventId) || payments.findByOrderId(UUID.fromString(order.orderId())).isPresent()) {
            return;
        }
        Payment.Method method = Payment.Method.valueOf(order.paymentMethod());
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setOrderId(UUID.fromString(order.orderId()));
        p.setOrderCode(order.orderCode());
        p.setUserId(order.userId());
        p.setCustomerEmail(order.customerEmail());
        p.setAmount(order.totalAmount());
        p.setMethod(method);
        p.setProvider(switch (method) {
            case COD -> "CASH";
            case VNPAY -> VnPayGateway.PROVIDER;
            case MOCK -> MockGateway.PROVIDER;
        });
        p.setStatus(method == Payment.Method.COD ? Payment.Status.AWAITING_COLLECTION : Payment.Status.PENDING);
        p.setTxnRef(order.orderCode());
        payments.save(p);
        log.info("Payment {} opened for order {} ({} {})", p.getId(), p.getOrderCode(), method, p.getAmount());
    }

    /** OrderCancelled: void a pending payment, or refund a captured one (compensation). */
    @Transactional
    public void onOrderCancelled(String eventId, OrderEvents.OrderCancelled event) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        payments.findByOrderId(UUID.fromString(event.orderId())).ifPresent(p -> {
            switch (p.getStatus()) {
                case PENDING, AWAITING_COLLECTION -> {
                    p.setStatus(Payment.Status.CANCELLED);
                    p.setFailureReason(event.reason());
                }
                case SUCCEEDED -> {
                    if (event.refundRequired()) {
                        refund(p, event.reason());
                    }
                }
                default -> {
                    // FAILED / CANCELLED / REFUNDED: nothing to undo
                }
            }
        });
    }

    /** COD: the shipper collected the cash when the order was delivered. */
    @Transactional
    public void onOrderStatusChanged(String eventId, OrderEvents.OrderStatusChanged event) {
        if (alreadyProcessed(eventId) || !"COMPLETED".equals(event.newStatus())) {
            return;
        }
        payments.findByOrderId(UUID.fromString(event.orderId()))
                .filter(p -> p.getStatus() == Payment.Status.AWAITING_COLLECTION)
                .ifPresent(p -> {
                    p.setStatus(Payment.Status.SUCCEEDED);
                    p.setPaidAt(Instant.now());
                    transactions.save(PaymentTransaction.of(p, PaymentTransaction.Type.COLLECT, "SUCCEEDED", null));
                });
    }

    // ---------- Customer-facing ----------

    @Transactional
    public CheckoutResponse checkout(CurrentUser user, UUID orderId, String clientIp) {
        Payment p = payments.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_READY",
                        "Payment is being prepared, please retry in a moment", 409));
        if (!p.getUserId().equals(user.id())) {
            throw BusinessException.notFound("Payment");
        }
        if (p.getStatus() != Payment.Status.PENDING) {
            throw BusinessException.conflict("PAYMENT_NOT_PENDING", "Payment is " + p.getStatus());
        }
        String url = switch (p.getMethod()) {
            case VNPAY -> vnPayGateway.checkoutUrl(p, clientIp);
            case MOCK -> mockGateway.checkoutUrl(p);
            case COD -> throw BusinessException.badRequest("COD", "Cash on delivery does not need online payment");
        };
        transactions.save(PaymentTransaction.of(p, PaymentTransaction.Type.CHECKOUT, "CREATED", url));
        return new CheckoutResponse(p.getId().toString(), p.getOrderId().toString(), p.getProvider(),
                p.getAmount(), url);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getForOrder(CurrentUser user, UUID orderId) {
        Payment p = payments.findByOrderId(orderId).orElseThrow(() -> BusinessException.notFound("Payment"));
        if (!p.getUserId().equals(user.id()) && !user.isStaff()) {
            throw BusinessException.notFound("Payment");
        }
        return PaymentResponse.from(p);
    }

    @Transactional(readOnly = true)
    public Payment requireByTxnRef(String txnRef) {
        return payments.findByTxnRef(txnRef).orElseThrow(() -> BusinessException.notFound("Payment"));
    }

    // ---------- Gateway callbacks ----------

    /**
     * Applies a verified gateway result. Idempotent: gateways retry IPNs, so a second identical
     * callback is acknowledged without side effects.
     */
    @Transactional
    public CallbackOutcome applyGatewayResult(GatewayResult result) {
        Payment p = payments.findByTxnRef(result.txnRef()).orElse(null);
        if (p == null) {
            return CallbackOutcome.NOT_FOUND;
        }
        if (p.getAmount() != result.amount()) {
            log.warn("Amount mismatch for {}: expected {} got {}", p.getTxnRef(), p.getAmount(), result.amount());
            return CallbackOutcome.AMOUNT_MISMATCH;
        }
        if (p.getStatus() != Payment.Status.PENDING && p.getStatus() != Payment.Status.CANCELLED) {
            return CallbackOutcome.ALREADY_PROCESSED;
        }
        boolean lateSuccess = p.getStatus() == Payment.Status.CANCELLED;
        if (lateSuccess && !result.success()) {
            return CallbackOutcome.ALREADY_PROCESSED;
        }
        p.setProviderTxnId(result.providerTxnId());
        transactions.save(PaymentTransaction.of(p, PaymentTransaction.Type.CHARGE,
                result.success() ? "SUCCEEDED" : "FAILED", result.rawPayload()));
        if (result.success()) {
            // Also covers money arriving after the order timed out: Order will reply with a refund request.
            p.setStatus(Payment.Status.SUCCEEDED);
            p.setPaidAt(Instant.now());
            p.setFailureReason(null);
            publish(Events.PAYMENT_SUCCEEDED, p, null);
        } else {
            p.setStatus(Payment.Status.FAILED);
            p.setFailureReason(result.reason());
            publish(Events.PAYMENT_FAILED, p, result.reason());
        }
        return CallbackOutcome.APPLIED;
    }

    // ---------- Back office ----------

    @Transactional(readOnly = true)
    public List<PaymentRepository.ReconciliationRow> reconcile(Instant from, Instant to) {
        return payments.reconcile(from, to);
    }

    @Transactional(readOnly = true)
    public List<PaymentTransaction> history(UUID paymentId) {
        return transactions.findByPaymentIdOrderByIdAsc(paymentId);
    }

    private void refund(Payment p, String reason) {
        // A real integration would call the gateway refund API here (VNPay: /merchant_webapi/api/transaction).
        p.setStatus(Payment.Status.REFUNDED);
        p.setRefundedAt(Instant.now());
        transactions.save(PaymentTransaction.of(p, PaymentTransaction.Type.REFUND, "SUCCEEDED", reason));
        publish(Events.PAYMENT_REFUNDED, p, reason);
        log.info("Refunded {} VND for order {}", p.getAmount(), p.getOrderCode());
    }

    private void publish(String type, Payment p, String reason) {
        outbox.append(Events.TOPIC_PAYMENT, type, p.getOrderId().toString(), new PaymentResult(
                p.getId().toString(), p.getOrderId().toString(), p.getOrderCode(), p.getUserId(), p.getCustomerEmail(),
                p.getAmount(), p.getMethod().name(), p.getProvider(), p.getStatus().name(), reason));
    }

    private boolean alreadyProcessed(String eventId) {
        if (processedEvents.existsById(eventId)) {
            return true;
        }
        processedEvents.save(new ProcessedEvent(eventId));
        return false;
    }
}
