package com.omni.payment.repository;

import com.omni.payment.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /** SKIP LOCKED lets several service replicas relay the outbox in parallel without duplicates. */
    @Query(value = """
            SELECT * FROM outbox_events WHERE published_at IS NULL
            ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(int limit);

    @Modifying
    @Query("delete from OutboxEvent e where e.publishedAt < :before")
    int deletePublishedBefore(Instant before);

    long countByPublishedAtIsNull();
}
