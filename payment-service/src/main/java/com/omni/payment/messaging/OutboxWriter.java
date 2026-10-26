package com.omni.payment.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.event.EventEnvelope;
import com.omni.payment.domain.OutboxEvent;
import com.omni.payment.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records an event in the outbox table. MANDATORY propagation guarantees it is always part of the
 * caller's business transaction: either both the state change and the event are stored, or neither.
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxRepository outbox;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, String eventType, String aggregateId, Object payload) {
        EventEnvelope envelope = EventEnvelope.of(eventType, aggregateId, payload, mapper);
        OutboxEvent row = new OutboxEvent();
        row.setId(UUID.fromString(envelope.eventId()));
        row.setAggregateType("Payment");
        row.setAggregateId(aggregateId);
        row.setEventType(eventType);
        row.setTopic(topic);
        try {
            row.setPayload(mapper.writeValueAsString(envelope));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + eventType, e);
        }
        outbox.save(row);
    }
}
