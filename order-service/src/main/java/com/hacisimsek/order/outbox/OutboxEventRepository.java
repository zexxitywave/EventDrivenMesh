package com.hacisimsek.order.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Fetch all pending events ordered oldest-first (for FIFO delivery) */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.Status status);

    /** Clean up published events older than the given cutoff to keep the table small */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'PUBLISHED' AND e.publishedAt < :cutoff")
    void deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
