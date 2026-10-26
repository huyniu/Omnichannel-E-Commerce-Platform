package com.omni.notification.service;

import com.omni.common.event.EventEnvelope;
import com.omni.notification.domain.NotificationLog;
import com.omni.notification.repository.NotificationLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Delivers notifications on two channels: e-mail (SMTP) and in-app inbox (MongoDB).
 * SMS / mobile push providers would plug in here as additional channels.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationLogRepository logs;
    private final JavaMailSender mailSender;
    private final MeterRegistry registry;

    @Value("${notification.mail-from:no-reply@omni.local}")
    private String mailFrom;

    @Value("${notification.email-enabled:true}")
    private boolean emailEnabled;

    public void handle(EventEnvelope event) {
        MessageTemplates.render(event).ifPresent(msg -> {
            deliverInApp(event, msg);
            deliverEmail(event, msg);
        });
    }

    private void deliverInApp(EventEnvelope event, MessageTemplates.Message msg) {
        if (msg.userId() == null || logs.existsByEventIdAndChannel(event.eventId(), NotificationLog.Channel.IN_APP)) {
            return;
        }
        save(event, msg, NotificationLog.Channel.IN_APP, String.valueOf(msg.userId()), NotificationLog.Status.SENT);
    }

    private void deliverEmail(EventEnvelope event, MessageTemplates.Message msg) {
        if (logs.existsByEventIdAndChannel(event.eventId(), NotificationLog.Channel.EMAIL)) {
            return;
        }
        if (!emailEnabled || !StringUtils.hasText(msg.email())) {
            save(event, msg, NotificationLog.Channel.EMAIL, msg.email(), NotificationLog.Status.SKIPPED);
            return;
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(mailFrom);
        mail.setTo(msg.email());
        mail.setSubject(msg.subject());
        mail.setText(msg.body());
        // Throws on SMTP failure: the Kafka error handler retries, then parks the event on the DLT.
        mailSender.send(mail);
        save(event, msg, NotificationLog.Channel.EMAIL, msg.email(), NotificationLog.Status.SENT);
        registry.counter("notifications.sent", "channel", "email", "event", event.eventType()).increment();
        log.info("Email '{}' sent to {}", msg.subject(), msg.email());
    }

    private void save(EventEnvelope event, MessageTemplates.Message msg, NotificationLog.Channel channel,
                      String recipient, NotificationLog.Status status) {
        NotificationLog entry = new NotificationLog();
        entry.setEventId(event.eventId());
        entry.setEventType(event.eventType());
        entry.setUserId(msg.userId());
        entry.setOrderId(msg.orderId());
        entry.setOrderCode(msg.orderCode());
        entry.setChannel(channel);
        entry.setRecipient(recipient);
        entry.setSubject(msg.subject());
        entry.setBody(msg.body());
        entry.setStatus(status);
        try {
            logs.save(entry);
        } catch (DuplicateKeyException e) {
            log.debug("Notification {} / {} already recorded", event.eventId(), channel);
        }
    }
}
