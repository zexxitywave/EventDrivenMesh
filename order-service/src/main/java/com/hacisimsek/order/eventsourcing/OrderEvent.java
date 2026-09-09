package com.hacisimsek.order.eventsourcing;

import com.hacisimsek.order.model.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable event record that captures every state transition of an Order.
 *
 * Append-only — rows are never updated or deleted.
 * The full history of an order is the ordered sequence of its OrderEvents.
 */
@Entity
@Table(name = "order_events",
        indexes = {
                @Index(name = "idx_order_events_order_id",   columnList = "order_id, occurred_at"),
                @Index(name = "idx_order_events_correlation", columnList = "correlation_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "new_status", nullable = false)
    private String newStatus;

    @Column(name = "previous_status")
    private String previousStatus;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "details", length = 500)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @PrePersist
    protected void onCreate() {
        this.occurredAt = Instant.now();
    }
}
