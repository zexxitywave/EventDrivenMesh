package com.hacisimsek.order.eventsourcing;

import com.hacisimsek.order.model.Order;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable event record that captures every state transition of an Order.
 *
 * This is the Event Sourcing append-only log. Every time an order's status
 * changes, a new OrderEvent row is inserted — never updated, never deleted.
 *
 * The full history of an order is the ordered sequence of its OrderEvents.
 * The current state can always be rebuilt by replaying them in sequence.
 *
 * Indexing:
 *  - orderId + occurredAt for chronological history queries
 *  - correlationId for saga-level tracing across services
 */
@Entity
@Table(name = "order_events",
        indexes = {
                @Index(name = "idx_order_events_order_id", columnList = "orderId, occurredAt"),
                @Index(name = "idx_order_events_correlation", columnList = "correlationId")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The order this event belongs to */
    @Column(nullable = false)
    private UUID orderId;

    /** Correlation ID that ties this event to the saga and to gateway logs */
    private UUID correlationId;

    /** The type of event — maps to OrderStatus transitions */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    /** The new status after this event */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Order.OrderStatus newStatus;

    /** The previous status before this event (null for ORDER_CREATED) */
    @Enumerated(EnumType.STRING)
    private Order.OrderStatus previousStatus;

    /** Who or what triggered this event (e.g. "inventory-service", "payment-service") */
    @Column(length = 100)
    private String triggeredBy;

    /** Optional reason/detail (e.g. failure reason, tracking number) */
    @Column(length = 500)
    private String details;

    /** When this event occurred — immutable, set at insert time */
    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    @PrePersist
    protected void onCreate() {
        this.occurredAt = Instant.now();
    }

    public enum EventType {
        ORDER_CREATED,
        INVENTORY_CHECKING,
        INVENTORY_RESERVED,
        INVENTORY_RESERVATION_FAILED,
        PAYMENT_PROCESSING,
        PAYMENT_COMPLETED,
        PAYMENT_FAILED,
        SHIPPING_PROCESSING,
        ORDER_SHIPPED,
        ORDER_COMPLETED,
        ORDER_CANCELLED,
        ORDER_FAILED
    }
}
