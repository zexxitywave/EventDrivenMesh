package com.hacisimsek.notification.saga;

import java.util.UUID;

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

    private final NotificationService notificationService;

    // ── Order events ──────────────────────────────────────────────────────────

    @KafkaListener(topics = "order-events", groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        log.debug("Received order event: {}", event != null ? event.getClass().getSimpleName() : "null");

        if (event instanceof OrderCreatedEvent e) {
            log.info("ORDER_PLACED event for order: {}, email: {}", e.getOrderId(), e.getCustomerEmail());
            notificationService.sendOrderPlacedNotification(e);
        }
    }

    // ── Payment events ────────────────────────────────────────────────────────

    @KafkaListener(topics = "payment-events", groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        log.debug("Received payment event: {}", event != null ? event.getClass().getSimpleName() : "null");

        if (event instanceof PaymentProcessedEvent e) {
            log.info("PAYMENT_SUCCESS event for order: {}, email: {}", e.getOrderId(), e.getCustomerEmail());
            notificationService.sendPaymentSuccessNotification(
                    e.getOrderId(),
                    e.getCustomerId() != null ? e.getCustomerId() : UUID.randomUUID(),
                    e.getCustomerEmail());

        } else if (event instanceof PaymentFailedEvent e) {
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
        log.debug("Received shipping event: {}", event != null ? event.getClass().getSimpleName() : "null");

        if (event instanceof ShipmentProcessedEvent e) {
            log.info("ORDER_SHIPPED event for order: {}", e.getOrderId());
            notificationService.sendOrderShippedNotification(
                    e.getOrderId(),
                    e.getCustomerId() != null ? e.getCustomerId() : UUID.randomUUID(),
                    e.getCustomerEmail(),
                    e.getTrackingNumber());

        } else if (event instanceof ShipmentFailedEvent e) {
            // Shipment failed — notify customer that order failed and refund is being processed
            log.warn("SHIPMENT_FAILED event for order: {}, reason: {}", e.getOrderId(), e.getReason());
            notificationService.sendShipmentFailedNotification(
                    e.getOrderId(),
                    e.getCustomerId() != null ? e.getCustomerId() : UUID.randomUUID(),
                    e.getCustomerEmail() != null ? e.getCustomerEmail() : null,
                    e.getReason() != null ? e.getReason() : "Shipment could not be processed");
        }
    }
}
