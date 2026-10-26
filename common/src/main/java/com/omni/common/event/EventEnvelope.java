package com.omni.common.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire format of every Kafka message. The message key is always {@code aggregateId}
 * so all events of one order land on the same partition and keep their order.
 * {@code eventId} is used by consumers for idempotent processing.
 */
public record EventEnvelope(String eventId, String eventType, String aggregateId, Instant occurredAt, JsonNode payload) {

    public static EventEnvelope of(String eventType, String aggregateId, Object payload, ObjectMapper mapper) {
        return new EventEnvelope(UUID.randomUUID().toString(), eventType, aggregateId, Instant.now(),
                mapper.valueToTree(payload));
    }

    public <T> T payloadAs(Class<T> type, ObjectMapper mapper) {
        try {
            return mapper.treeToValue(payload, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot read payload of " + eventType + " as " + type.getSimpleName(), e);
        }
    }
}
