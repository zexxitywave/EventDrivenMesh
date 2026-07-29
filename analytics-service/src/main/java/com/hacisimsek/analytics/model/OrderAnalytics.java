package com.hacisimsek.analytics.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Flattened view of an OrderCreatedEvent stored in PostgreSQL.
 * Used for SQL-based business analytics — revenue, top customers, trends.
 */
@Entity
@Table(name = "order_analytics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Saga / event tracking ──────────────────────────────────────────────
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "event_timestamp")
    private Instant eventTimestamp;

    // ── Order fields ───────────────────────────────────────────────────────
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "item_count")
    private Integer itemCount;

    // ── Metadata ───────────────────────────────────────────────────────────
    @Column(name = "ingested_at")
    private Instant ingestedAt;
}
