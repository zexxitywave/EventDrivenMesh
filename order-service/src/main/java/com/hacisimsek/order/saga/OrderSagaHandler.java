package com.hacisimsek.order.saga;

import com.hacisimsek.common.event.inventory.InventoryReservationFailedEvent;
import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.event.shipping.ShipmentFailedEvent;
import com.hacisimsek.common.event.shipping.ShipmentProcessedEvent;
import com.hacisimsek.order.saga.orchestrator.OrderSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listeners for the Order Saga.
 *
 * This class is now a thin adapter layer — it receives Kafka events and
 * immediately delegates to the {@link OrderSagaOrchestrator} which owns
 * all the state machine logic and compensation decisions.
 *
 * All business logic that was previously inline here has moved to the orchestrator.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaHandler {

    private final OrderSagaOrchestrator orchestrator;

    // ── Inventory events ──────────────────────────────────────────────────────

    @KafkaListener(topics = "inventory-events", groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleInventoryEvents(Object event) {
        log.debug("Received inventory event: {}", event.getClass().getSimpleName());

        if (event instanceof InventoryReservedEvent e) {
            orchestrator.onInventoryReserved(e.getOrderId(), e.getCorrelationId());

        } else if (event instanceof InventoryReservationFailedEvent e) {
            orchestrator.onInventoryFailed(e.getOrderId(), e.getCorrelationId(),
                    e.getReason() != null ? e.getReason() : "unknown");
        }
    }

    // ── Payment events ────────────────────────────────────────────────────────

    @KafkaListener(topics = "payment-events", groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentEvents(Object event) {
        log.debug("Received payment event: {}", event.getClass().getSimpleName());

        if (event instanceof PaymentProcessedEvent e) {
            orchestrator.onPaymentCompleted(e.getOrderId(), e.getCorrelationId(), e.getPaymentId());

        } else if (event instanceof PaymentFailedEvent e) {
            orchestrator.onPaymentFailed(e.getOrderId(), e.getCorrelationId(),
                    e.getReason() != null ? e.getReason() : "unknown");
        }
    }

    // ── Shipping events ───────────────────────────────────────────────────────

    @KafkaListener(topics = "shipping-events", groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleShippingEvents(Object event) {
        log.debug("Received shipping event: {}", event.getClass().getSimpleName());

        if (event instanceof ShipmentProcessedEvent e) {
            orchestrator.onShipmentCreated(e.getOrderId(), e.getCorrelationId(), e.getTrackingNumber());

        } else if (event instanceof ShipmentFailedEvent e) {
            orchestrator.onShipmentFailed(e.getOrderId(), e.getCorrelationId(),
                    e.getReason() != null ? e.getReason() : "unknown");
        }
    }
}
