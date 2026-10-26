package com.omni.order.messaging;

import com.omni.order.domain.OutboxEvent;
import com.omni.order.repository.OutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polling publisher of the transactional outbox. If Kafka is down, rows simply stay unpublished and
 * are retried on the next tick: no event is ever lost. Delivery is at-least-once, so consumers
 * de-duplicate by eventId.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 200;

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka, MeterRegistry registry) {
        this.outbox = outbox;
        this.kafka = kafka;
        Gauge.builder("outbox.pending", outbox, OutboxRepository::countByPublishedAtIsNull).register(registry);
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:300}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = outbox.lockNextBatch(BATCH_SIZE);
        for (OutboxEvent event : batch) {
            try {
                kafka.send(event.getTopic(), event.getAggregateId(), event.getPayload()).get(5, TimeUnit.SECONDS);
                event.setPublishedAt(Instant.now());
            } catch (Exception e) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(e.getClass().getSimpleName() + ": " + e.getMessage());
                log.warn("Outbox relay failed for {} ({}), will retry: {}", event.getId(), event.getEventType(), e.getMessage());
                // Stop here to preserve per-order event ordering; remaining rows go next tick.
                break;
            }
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeOld() {
        int deleted = outbox.deletePublishedBefore(Instant.now().minus(Duration.ofDays(7)));
        log.info("Purged {} published outbox rows", deleted);
    }
}
