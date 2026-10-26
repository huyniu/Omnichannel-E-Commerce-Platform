package com.omni.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.event.EventEnvelope;
import com.omni.common.event.Events;
import com.omni.common.event.PaymentEvents.PaymentResult;
import com.omni.order.service.OrderService;
import com.omni.order.service.OrderTxService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Saga participant (choreography): reacts to the outcome of the payment step.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsListener.class);

    private final ObjectMapper mapper;
    private final OrderTxService tx;
    private final OrderService orderService;

    @KafkaListener(topics = Events.TOPIC_PAYMENT, groupId = "order-service")
    public void onMessage(String message) throws Exception {
        EventEnvelope event = mapper.readValue(message, EventEnvelope.class);
        PaymentResult payment = event.payloadAs(PaymentResult.class, mapper);
        UUID orderId = UUID.fromString(payment.orderId());
        log.info("Received {} for order {}", event.eventType(), payment.orderCode());
        switch (event.eventType()) {
            case Events.PAYMENT_SUCCEEDED -> tx.onPaymentSucceeded(event.eventId(), orderId);
            case Events.PAYMENT_FAILED -> orderService.cancelPending(event.eventId(), orderId,
                    "Payment failed: " + payment.reason(), "payment-service");
            case Events.PAYMENT_REFUNDED -> tx.onPaymentRefunded(event.eventId(), orderId);
            default -> log.debug("Ignoring payment event {}", event.eventType());
        }
    }
}
