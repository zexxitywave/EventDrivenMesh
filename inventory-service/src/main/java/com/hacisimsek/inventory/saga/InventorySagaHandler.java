package com.hacisimsek.inventory.saga;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.shipping.ShipmentFailedEvent;
import com.hacisimsek.common.logging.LogPublisher;
import com.hacisimsek.inventory.service.InventoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventorySagaHandler {

    private static final String SERVICE_NAME = "inventory-service";

    private final InventoryService inventoryService;
    private final LogPublisher logPublisher;

    // ── Forward flow: reserve stock when a new order arrives ─────────────────

    @KafkaListener(
            topics = "order-events",
            groupId = "inventory-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderEvents(ConsumerRecord<String, Object> record) {

        Object event = record.value();
        log.debug("Received order event: {}", event != null ? event.getClass().getSimpleName() : "null");

        try {
            if (event instanceof OrderCreatedEvent orderCreatedEvent) {
                log.info("Processing OrderCreatedEvent for order: {}", orderCreatedEvent.getOrderId());
                inventoryService.reserveInventory(orderCreatedEvent);
            } else {
                log.warn("Unhandled event type on order-events: {}",
                        event != null ? event.getClass().getName() : "null");
            }
        } catch (Exception e) {
            log.error("Error processing order event", e);
        }
    }

    // ── Compensation: release stock when payment fails ────────────────────────
    //
    // When payment fails the order is already FAILED in order-service, but the
    // reserved stock still sits locked in inventory. Without releasing it the
    // stock is permanently unavailable — this is the compensation step.

    @KafkaListener(
            topics = "payment-events",
            groupId = "inventory-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentEvents(ConsumerRecord<String, Object> record) {

        Object event = record.value();
        log.debug("Received payment event: {}", event != null ? event.getClass().getSimpleName() : "null");

        if (event instanceof PaymentFailedEvent paymentFailedEvent) {
            log.warn("Payment failed for order: {} — releasing reserved inventory. Reason: {}",
                    paymentFailedEvent.getOrderId(), paymentFailedEvent.getReason());
            try {
                inventoryService.cancelReservation(paymentFailedEvent.getOrderId());

                logPublisher.warn(SERVICE_NAME,
                        paymentFailedEvent.getCorrelationId() != null
                                ? paymentFailedEvent.getCorrelationId().toString() : null,
                        "Inventory released (compensation) — payment failed for order: "
                                + paymentFailedEvent.getOrderId(),
                        Map.of(
                                "orderId", paymentFailedEvent.getOrderId().toString(),
                                "reason", paymentFailedEvent.getReason() != null
                                        ? paymentFailedEvent.getReason() : "unknown",
                                "compensationAction", "STOCK_RELEASED"
                        ));
            } catch (Exception e) {
                // Log but don't rethrow — a missing reservation (e.g. already cancelled)
                // must not block other messages in the partition
                log.error("Failed to release inventory for order {} after payment failure: {}",
                        paymentFailedEvent.getOrderId(), e.getMessage());
                logPublisher.error(SERVICE_NAME,
                        paymentFailedEvent.getCorrelationId() != null
                                ? paymentFailedEvent.getCorrelationId().toString() : null,
                        "Compensation failed — could not release inventory for order: "
                                + paymentFailedEvent.getOrderId(),
                        e,
                        Map.of("orderId", paymentFailedEvent.getOrderId().toString()));
            }
        }
        // PaymentProcessedEvent is intentionally ignored here — the stock was
        // already deducted at reservation time. Shipping failure handles release below.
    }

    // ── Compensation: release stock when shipment fails ───────────────────────
    //
    // If shipping fails after a successful payment, the order is FAILED but
    // the reserved (already deducted) stock must be put back. In a real system
    // the payment would also be refunded — that is handled in payment-service.

    @KafkaListener(
            topics = "shipping-events",
            groupId = "inventory-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleShippingEvents(ConsumerRecord<String, Object> record) {

        Object event = record.value();
        log.debug("Received shipping event: {}", event != null ? event.getClass().getSimpleName() : "null");

        if (event instanceof ShipmentFailedEvent shipmentFailedEvent) {
            log.warn("Shipment failed for order: {} — releasing reserved inventory. Reason: {}",
                    shipmentFailedEvent.getOrderId(), shipmentFailedEvent.getReason());
            try {
                inventoryService.cancelReservation(shipmentFailedEvent.getOrderId());

                logPublisher.warn(SERVICE_NAME,
                        shipmentFailedEvent.getCorrelationId() != null
                                ? shipmentFailedEvent.getCorrelationId().toString() : null,
                        "Inventory released (compensation) — shipment failed for order: "
                                + shipmentFailedEvent.getOrderId(),
                        Map.of(
                                "orderId", shipmentFailedEvent.getOrderId().toString(),
                                "reason", shipmentFailedEvent.getReason() != null
                                        ? shipmentFailedEvent.getReason() : "unknown",
                                "compensationAction", "STOCK_RELEASED"
                        ));
            } catch (Exception e) {
                log.error("Failed to release inventory for order {} after shipment failure: {}",
                        shipmentFailedEvent.getOrderId(), e.getMessage());
                logPublisher.error(SERVICE_NAME,
                        shipmentFailedEvent.getCorrelationId() != null
                                ? shipmentFailedEvent.getCorrelationId().toString() : null,
                        "Compensation failed — could not release inventory for order: "
                                + shipmentFailedEvent.getOrderId(),
                        e,
                        Map.of("orderId", shipmentFailedEvent.getOrderId().toString()));
            }
        }
        // ShipmentProcessedEvent is intentionally ignored — stock was already
        // correctly deducted at reservation time and confirmed through payment.
    }
}
