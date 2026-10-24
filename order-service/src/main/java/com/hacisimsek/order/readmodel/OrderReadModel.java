package com.hacisimsek.order.readmodel;

import com.hacisimsek.order.model.Order;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * CQRS read model for orders.
 *
 * <p>This is the READ side of CQRS. It is a denormalized snapshot of an order,
 * projected from Kafka events by {@link OrderReadProjection}. The write side
 * (table {@code orders}) remains the source of truth for the saga; this table
 * exists purely to serve fast, pre-joined reads without touching the write
 * table, its items, or its indexes.
 */
@Entity
@Table(name = "order_reads",
        indexes = {
                @Index(name = "idx_order_reads_customer_id", columnList = "customer_id"),
                @Index(name = "idx_order_reads_status", columnList = "status")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReadModel {

    @Id
    @Column(name = "order_id", updatable = false, nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Column(name = "item_count")
    private Integer itemCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Order.OrderStatus status;

    /** True once a PaymentRefundedEvent has been projected (compensation). */
    @Column(name = "refunded")
    private boolean refunded;

    /** True once a StockReleasedEvent has been projected (compensation). */
    @Column(name = "stock_released")
    private boolean stockReleased;

    /**
     * Denormalized items snapshot (JSON) so reads never have to join the
     * {@code order_items} table. Kept as text to avoid a child-entity fetch.
     */
    @Column(name = "items_json", columnDefinition = "TEXT")
    private String itemsJson;

    /** Simple class name of the Kafka event that produced the latest update. */
    @Column(name = "last_event")
    private String lastEvent;

    /** Timestamp of that event (from the event itself, not local now()). */
    @Column(name = "last_event_at")
    private Instant lastEventAt;

    /** Order creation time, set from OrderCreatedEvent, only if absent. */
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.updatedAt = Instant.now();
    }
}