package com.omni.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omni.common.event.EventEnvelope;
import com.omni.common.event.Events;
import com.omni.common.event.OrderEvents;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTemplatesTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void rendersCancellationWithRefundNotice() {
        EventEnvelope event = EventEnvelope.of(Events.ORDER_CANCELLED, "o1",
                new OrderEvents.OrderCancelled("o1", "OD1", 7L, "c@x.vn", "Payment timeout", true), mapper);

        var msg = MessageTemplates.render(event).orElseThrow();

        assertThat(msg.userId()).isEqualTo(7L);
        assertThat(msg.email()).isEqualTo("c@x.vn");
        assertThat(msg.subject()).contains("OD1");
        assertThat(msg.body()).contains("Payment timeout").contains("hoàn lại");
    }

    @Test
    void ignoresUnknownEvents() {
        EventEnvelope event = EventEnvelope.of("SomethingElse", "o1", Map.of(), mapper);
        assertThat(MessageTemplates.render(event)).isEmpty();
    }
}
