package com.hacisimsek.order.eventsourcing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the append-only order_events table.
 *
 * Events are always inserted, never updated or deleted.
 * Queries return events in chronological order.
 */
public interface OrderEventRepository extends JpaRepository<OrderEvent, UUID> {

    /** Full audit trail for one order — ordered oldest first */
    List<OrderEvent> findByOrderIdOrderByOccurredAtAsc(UUID orderId);

    /** All events tied to a saga correlation ID — cross-service audit */
    List<OrderEvent> findByCorrelationIdOrderByOccurredAtAsc(UUID correlationId);
}
