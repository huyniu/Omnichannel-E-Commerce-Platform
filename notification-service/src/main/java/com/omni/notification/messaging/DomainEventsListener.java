package com.omni.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.event.EventEnvelope;
import com.omni.common.event.Events;
import com.omni.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Pure event consumer: no other service calls Notification synchronously, so when it is down orders keep
 * flowing and the backlog is delivered from Kafka once it comes back.
 */
@Component
@RequiredArgsConstructor
public class DomainEventsListener {

    private final ObjectMapper mapper;
    private final NotificationService notificationService;

    @KafkaListener(topics = {Events.TOPIC_ORDER, Events.TOPIC_PAYMENT}, groupId = "notification-service")
    public void onMessage(String message) throws Exception {
        notificationService.handle(mapper.readValue(message, EventEnvelope.class));
    }
}
