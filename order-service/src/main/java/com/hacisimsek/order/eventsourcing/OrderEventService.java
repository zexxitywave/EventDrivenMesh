package com.hacisimsek.order.eventsourcing;

import com.hacisimsek.order.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for appending and querying order events (Event Sourcing log).
 *
 * Every call to {@link #append} inserts one immutable row into order_events.
 * The event log is the source of truth for what happened to an order and when.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventService {

    private final OrderEventRepository orderEventRepository;

    /**
     * Append a new event to the order's event log.
     * Called inside the same transaction as the Order status update.
     */
    @Transactional
    public OrderEvent append(UUID orderId,
                             UUID correlationId,
                             OrderEvent.EventType eventType,
                             Order.OrderStatus previousStatus,
                             Order.OrderStatus newStatus,
                             String triggeredBy,
                             String details) {
        OrderEvent event = OrderEvent.builder()
                .orderId(orderId)
                .correlationId(correlationId)
                .eventType(eventType)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .triggeredBy(triggeredBy)
                .details(details)
                .build();

        OrderEvent saved = orderEventRepository.save(event);
        log.debug("[EventStore] Appended {} for order {} ({} → {})",
                eventType, orderId, previousStatus, newStatus);
        return saved;
    }

    /** Retrieve the full immutable event log for an order */
    @Transactional(readOnly = true)
    public List<OrderEvent> getHistory(UUID orderId) {
        return orderEventRepository.findByOrderIdOrderByOccurredAtAsc(orderId);
    }

    /** Retrieve all events tied to a saga correlation ID */
    @Transactional(readOnly = true)
    public List<OrderEvent> getByCorrelationId(UUID correlationId) {
        return orderEventRepository.findByCorrelationIdOrderByOccurredAtAsc(correlationId);
    }

    /**
     * Rebuild current order status by replaying the event log.
     * Useful for auditing or reconciling against the Order table.
     */
    @Transactional(readOnly = true)
    public Order.OrderStatus rebuildCurrentStatus(UUID orderId) {
        List<OrderEvent> events = getHistory(orderId);
        if (events.isEmpty()) {
            throw new RuntimeException("No events found for order: " + orderId);
        }
        // The last event's newStatus is the current state
        return events.get(events.size() - 1).getNewStatus();
    }
}
