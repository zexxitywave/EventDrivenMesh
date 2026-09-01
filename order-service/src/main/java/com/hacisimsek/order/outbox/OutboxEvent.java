package com.hacisimsek.order.outbox;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox table entry — written in the same DB transaction as the Order.
 *
 * The OutboxPublisher reads unpublished rows on a fixed schedule and
 * publishes them to Kafka. On success the row is marked PUBLISHED.
 * On failure it stays PENDING and is retried on the next schedule tick.
 *
 * This guarantees at-least-once Kafka delivery even if the service
 * crashes between saving the order and sending to Kafka.
 */
@Entity
@Table(name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_status_created", columnList = "status, createdAt"),
                @Index(name = "idx_outbox_aggregate", columnList = "aggregateId")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The Kafka topic this event should be published to */
    @Column(nullable = false)
    private String topic;

    /** The entity this event belongs to — used as the Kafka message key */
    @Column(nullable = false)
    private UUID aggregateId;

    /** Fully-qualified Java class name of the payload (e.g. OrderCreatedEvent) */
    @Column(nullable = false)
    private String eventType;

    /** JSON-serialized event payload */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant publishedAt;

    /** Number of failed publish attempts — for observability */
    @Builder.Default
    private int retryCount = 0;

    /** Last error message from a failed publish attempt */
    @Column(length = 1000)
    private String lastError;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public enum Status {
        PENDING,
        PUBLISHED,
        FAILED   // after max retries exceeded (currently 5)
    }
}
