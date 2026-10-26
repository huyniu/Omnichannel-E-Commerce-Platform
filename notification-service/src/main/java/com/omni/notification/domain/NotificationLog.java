package com.omni.notification.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Delivery journal. The unique (eventId, channel) index makes consumption idempotent:
 * a redelivered Kafka message never sends the same e-mail twice.
 */
@Document("notification_logs")
@CompoundIndex(name = "uniq_event_channel", def = "{'eventId': 1, 'channel': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
public class NotificationLog {

    public enum Channel { EMAIL, IN_APP }

    public enum Status { SENT, SKIPPED }

    @Id
    private String id;

    private String eventId;

    private String eventType;

    @Indexed
    private Long userId;

    private String orderId;

    private String orderCode;

    private Channel channel;

    private String recipient;

    private String subject;

    private String body;

    private Status status;

    private boolean read;

    @Indexed
    private Instant createdAt = Instant.now();
}
