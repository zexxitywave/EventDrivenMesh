package com.hacisimsek.inventory.saga;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.common.event.inventory.StockReleasedEvent;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.shipping.ShipmentFailedEvent;
import com.hacisimsek.common.logging.LogPublisher;
import com.hacisimsek.inventory.service.InventoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventorySagaHandler {

    private static final String SERVICE_NAME = "inventory-service";

    // Class-name constants — compared against event.getClass().getName() to avoid
    // classloader-identity issues where instanceof silently returns false even
    // though the class name and bytecode are identical (TCCL vs. app classloader).
    private static final String ORDER_CREATED = OrderCreatedEvent.class.getName();
    private static final String PAYMENT_FAILED = PaymentFailedEvent.class.getName();
    private static final String SHIPMENT_FAILED = ShipmentFailedEvent.class.getName();

    private final InventoryService inventoryService;
    private final LogPublisher logPublisher;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String COMPENSATION_TOPIC = "compensation-events";

    // ── Forward flow: reserve stock when a new order arrives ─────────────────

    @KafkaListener(
            topics = "order-events",
            groupId = "inventory-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderEvents(ConsumerRecord<String, Object> record) {

        Object event = record.value();
        String type = event != null ? event.getClass().getName() : null;
        log.info("[InventorySagaHandler] order-events type={}", type);

        if (ORDER_CREATED.equals(type)) {
            // Use objectMapper.convertValue so we get a properly typed object even
            // if the deserializer returned a LinkedHashMap or a different CL instance.
            OrderCreatedEvent orderCreatedEvent = objectMapper.convertValue(event, OrderCreatedEvent.class);
            log.info("Processing OrderCreatedEvent for order: {}", orderCreatedEvent.getOrderId());
            // Let exceptions propagate — DefaultErrorHandler will retry then DLQ.
            // Swallowing here would ACK the message as success and lose it forever.
            inventoryService.reserveInventory(orderCreatedEvent);
        } else {
            log.warn("Unhandled event type on order-events: {}", type);
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
        String type = event != null ? event.getClass().getName() : null;
        log.debug("[InventorySagaHandler] payment-events type={}", type);

        if (PAYMENT_FAILED.equals(type)) {
            PaymentFailedEvent paymentFailedEvent = objectMapper.convertValue(event, PaymentFailedEvent.class);
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
                publishStockReleased(paymentFailedEvent.getCorrelationId(),
                        paymentFailedEvent.getOrderId(),
                        paymentFailedEvent.getReason() != null ? paymentFailedEvent.getReason() : "payment failed");
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
        String type = event != null ? event.getClass().getName() : null;
        log.debug("[InventorySagaHandler] shipping-events type={}", type);

        if (SHIPMENT_FAILED.equals(type)) {
            ShipmentFailedEvent shipmentFailedEvent = objectMapper.convertValue(event, ShipmentFailedEvent.class);
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
                publishStockReleased(shipmentFailedEvent.getCorrelationId(),
                        shipmentFailedEvent.getOrderId(),
                        shipmentFailedEvent.getReason() != null ? shipmentFailedEvent.getReason() : "shipment failed");
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

    /**
     * Publishes a StockReleasedEvent so order-service can record the
     * INVENTORY_RELEASED compensation step in the order event log.
     */
    private void publishStockReleased(java.util.UUID correlationId, java.util.UUID orderId, String reason) {
        try {
            StockReleasedEvent releaseEvent = new StockReleasedEvent(
                    correlationId, orderId, null, null, null, reason);
            kafkaTemplate.send(COMPENSATION_TOPIC, releaseEvent);
            log.info("StockReleasedEvent published for order {} on {}", orderId, COMPENSATION_TOPIC);
        } catch (Exception e) {
            // Release already succeeded — log only
            log.error("Failed to publish StockReleasedEvent for order {}: {}", orderId, e.getMessage());
        }
    }
}
