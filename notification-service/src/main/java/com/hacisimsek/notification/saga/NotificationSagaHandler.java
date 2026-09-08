package com.hacisimsek.notification.saga;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.common.event.inventory.InventoryReservationFailedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.event.shipping.ShipmentFailedEvent;
import com.hacisimsek.common.event.shipping.ShipmentProcessedEvent;
import com.hacisimsek.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSagaHandler {

    private static final String ORDER_CREATED        = OrderCreatedEvent.class.getName();
    private static final String INVENTORY_FAILED     = InventoryReservationFailedEvent.class.getName();
    private static final String PAYMENT_PROCESSED    = PaymentProcessedEvent.class.getName();
    private static final String PAYMENT_FAILED       = PaymentFailedEvent.class.getName();
    private static final String SHIPMENT_PROCESSED   = ShipmentProcessedEvent.class.getName();
    private static final String SHIPMENT_FAILED      = ShipmentFailedEvent.class.getName();

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // ── Order events ──────────────────────────────────────────────────────────

    @KafkaListener(topics = "order-events", groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        String type = event != null ? event.getClass().getName() : null;
        if (ORDER_CREATED.equals(type)) {
            OrderCreatedEvent e = objectMapper.convertValue(event, OrderCreatedEvent.class);
            log.info("ORDER_PLACED for order: {}", e.getOrderId());
            notificationService.sendOrderPlacedNotification(e);
        }
    }

    // ── Inventory events ──────────────────────────────────────────────────────

    @KafkaListener(topics = "inventory-events", groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleInventoryEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        String type = event != null ? event.getClass().getName() : null;
        if (INVENTORY_FAILED.equals(type)) {
            InventoryReservationFailedEvent e =
                    objectMapper.convertValue(event, InventoryReservationFailedEvent.class);
            log.warn("INVENTORY_FAILED for order: {} — notifying customer", e.getOrderId());
            if (e.getCustomerEmail() != null && !e.getCustomerEmail().isBlank()) {
                notificationService.sendOrderCancelledNotification(
                        e.getOrderId(),
                        e.getCustomerId(),
                        e.getCustomerEmail(),
                        e.getReason() != null ? e.getReason() : "Item out of stock");
            }
        }
    }

    // ── Payment events ────────────────────────────────────────────────────────

    @KafkaListener(topics = "payment-events", groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        String type = event != null ? event.getClass().getName() : null;
        log.debug("Received payment event type: {}", type);

        if (PAYMENT_PROCESSED.equals(type)) {
            PaymentProcessedEvent e = objectMapper.convertValue(event, PaymentProcessedEvent.class);
            log.info("PAYMENT_SUCCESS event for order: {}, email: {}", e.getOrderId(), e.getCustomerEmail());
            notificationService.sendPaymentSuccessNotification(
                    e.getOrderId(),
                    e.getCustomerId() != null ? e.getCustomerId() : UUID.randomUUID(),
                    e.getCustomerEmail());

        } else if (PAYMENT_FAILED.equals(type)) {
            PaymentFailedEvent e = objectMapper.convertValue(event, PaymentFailedEvent.class);
            log.info("PAYMENT_FAILED event for order: {}, email: {}", e.getOrderId(), e.getCustomerEmail());
            notificationService.sendPaymentFailedNotification(
                    e.getOrderId(),
                    e.getCustomerId() != null ? e.getCustomerId() : UUID.randomUUID(),
                    e.getCustomerEmail());
        }
    }

    // ── Shipping events ───────────────────────────────────────────────────────

    @KafkaListener(topics = "shipping-events", groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleShippingEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        String type = event != null ? event.getClass().getName() : null;
        log.debug("Received shipping event type: {}", type);

        if (SHIPMENT_PROCESSED.equals(type)) {
            ShipmentProcessedEvent e = objectMapper.convertValue(event, ShipmentProcessedEvent.class);
            log.info("ORDER_SHIPPED event for order: {}", e.getOrderId());
            notificationService.sendOrderShippedNotification(
                    e.getOrderId(),
                    e.getCustomerId() != null ? e.getCustomerId() : UUID.randomUUID(),
                    e.getCustomerEmail(),
                    e.getTrackingNumber());

        } else if (SHIPMENT_FAILED.equals(type)) {
            ShipmentFailedEvent e = objectMapper.convertValue(event, ShipmentFailedEvent.class);
            log.warn("SHIPMENT_FAILED event for order: {}, reason: {}", e.getOrderId(), e.getReason());
            notificationService.sendShipmentFailedNotification(
                    e.getOrderId(),
                    e.getCustomerId() != null ? e.getCustomerId() : UUID.randomUUID(),
                    e.getCustomerEmail() != null ? e.getCustomerEmail() : null,
                    e.getReason() != null ? e.getReason() : "Shipment could not be processed");
        }
    }
}
