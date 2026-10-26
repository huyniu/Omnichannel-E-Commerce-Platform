package com.omni.payment.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.event.EventEnvelope;
import com.omni.common.event.Events;
import com.omni.common.event.OrderEvents;
import com.omni.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final ObjectMapper mapper;
    private final PaymentService paymentService;

    @KafkaListener(topics = Events.TOPIC_ORDER, groupId = "payment-service")
    public void onMessage(String message) throws Exception {
        EventEnvelope event = mapper.readValue(message, EventEnvelope.class);
        switch (event.eventType()) {
            case Events.ORDER_CREATED ->
                    paymentService.onOrderCreated(event.eventId(), event.payloadAs(OrderEvents.OrderCreated.class, mapper));
            case Events.ORDER_CANCELLED ->
                    paymentService.onOrderCancelled(event.eventId(), event.payloadAs(OrderEvents.OrderCancelled.class, mapper));
            case Events.ORDER_STATUS_CHANGED ->
                    paymentService.onOrderStatusChanged(event.eventId(), event.payloadAs(OrderEvents.OrderStatusChanged.class, mapper));
            default -> log.debug("Ignoring order event {}", event.eventType());
        }
    }
}
