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
 * Thin adapter: receives events, delegates to OrderSagaOrchestrator.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaHandler {

    private final OrderSagaOrchestrator orchestrator;

    // Inventory events

    @KafkaListener(topics = "inventory-events", groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleInventoryEvents(Object event) {
        log.info("[SagaHandler] Received inventory event: type={}",
                event != null ? event.getClass().getName() : "null");

        if (event instanceof InventoryReservedEvent e) {
            log.info("[SagaHandler] Matched InventoryReservedEvent for order={}", e.getOrderId());
            orchestrator.onInventoryReserved(e.getOrderId(), e.getCorrelationId());

        } else if (event instanceof InventoryReservationFailedEvent e) {
            log.info("[SagaHandler] Matched InventoryReservationFailedEvent for order={}", e.getOrderId());
            orchestrator.onInventoryFailed(e.getOrderId(), e.getCorrelationId(),
                    e.getReason() != null ? e.getReason() : "unknown");

        } else {
            log.warn("[SagaHandler] No match for inventory event type={} — instanceof checks failed. " +
                     "ClassLoader: {}, InventoryReservedEvent loader: {}",
                    event != null ? event.getClass().getName() : "null",
                    event != null ? event.getClass().getClassLoader() : "N/A",
                    InventoryReservedEvent.class.getClassLoader());
        }
    }

    // Payment events

    @KafkaListener(topics = "payment-events", groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentEvents(Object event) {
        log.info("[SagaHandler] Received payment event: type={}",
                event != null ? event.getClass().getName() : "null");

        if (event instanceof PaymentProcessedEvent e) {
            orchestrator.onPaymentCompleted(e.getOrderId(), e.getCorrelationId(), e.getPaymentId());

        } else if (event instanceof PaymentFailedEvent e) {
            orchestrator.onPaymentFailed(e.getOrderId(), e.getCorrelationId(),
                    e.getReason() != null ? e.getReason() : "unknown");

        } else {
            log.warn("[SagaHandler] Unhandled payment event type: {}",
                    event != null ? event.getClass().getName() : "null");
        }
    }

    // Shipping events

    @KafkaListener(topics = "shipping-events", groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleShippingEvents(Object event) {
        log.info("[SagaHandler] Received shipping event: type={}",
                event != null ? event.getClass().getName() : "null");

        if (event instanceof ShipmentProcessedEvent e) {
            orchestrator.onShipmentCreated(e.getOrderId(), e.getCorrelationId(), e.getTrackingNumber());

        } else if (event instanceof ShipmentFailedEvent e) {
            orchestrator.onShipmentFailed(e.getOrderId(), e.getCorrelationId(),
                    e.getReason() != null ? e.getReason() : "unknown");

        } else {
            log.warn("[SagaHandler] Unhandled shipping event type: {}",
                    event != null ? event.getClass().getName() : "null");
        }
    }
}