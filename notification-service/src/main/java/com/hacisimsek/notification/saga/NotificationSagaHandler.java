package com.hacisimsek.notification.saga;

import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.event.shipping.ShipmentProcessedEvent;
import com.hacisimsek.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSagaHandler {

    private final NotificationService notificationService;

    // ─────────────────────────────────────────────────────────
    // ORDER EVENTS
    // ─────────────────────────────────────────────────────────

    @KafkaListener(
            topics = "order-events",
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderEvents(ConsumerRecord<String, Object> record) {

        Object event = record.value();

        log.info("Received event class: {}", event.getClass().getName());

        if (event instanceof OrderCreatedEvent e) {

            log.info("ORDER_PLACED event for order: {}, email: {}", e.getOrderId(), e.getCustomerEmail());

            // Use full-event overload so the PDF invoice is generated and attached
            notificationService.sendOrderPlacedNotification(e);
        }
    }

    // ─────────────────────────────────────────────────────────
    // PAYMENT EVENTS
    // ─────────────────────────────────────────────────────────

    @KafkaListener(
            topics = "payment-events",
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentEvents(ConsumerRecord<String, Object> record) {

        Object event = record.value();

        log.info("Received event class: {}", event.getClass().getName());

        if (event instanceof PaymentProcessedEvent e) {

            log.info("PAYMENT_SUCCESS event for order: {}, email: {}", e.getOrderId(), e.getCustomerEmail());

            notificationService.sendPaymentSuccessNotification(
                    e.getOrderId(),
                    e.getCustomerId() != null
                            ? e.getCustomerId()
                            : UUID.randomUUID(),
                    e.getCustomerEmail()   // was null — now uses the email from the event
            );

        } else if (event instanceof PaymentFailedEvent e) {

            log.info("PAYMENT_FAILED event for order: {}, email: {}", e.getOrderId(), e.getCustomerEmail());

            notificationService.sendPaymentFailedNotification(
                    e.getOrderId(),
                    e.getCustomerId() != null ? e.getCustomerId() : UUID.randomUUID(),
                    e.getCustomerEmail()   // now populated from the enriched event
            );
        }
    }

    // ─────────────────────────────────────────────────────────
    // SHIPPING EVENTS
    // ─────────────────────────────────────────────────────────

    @KafkaListener(
            topics = "shipping-events",
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleShippingEvents(ConsumerRecord<String, Object> record) {

        Object event = record.value();

        log.info("Received event class: {}", event.getClass().getName());

        if (event instanceof ShipmentProcessedEvent e) {

            log.info("ORDER_SHIPPED event for order: {}", e.getOrderId());

            notificationService.sendOrderShippedNotification(
                    e.getOrderId(),
                    e.getCustomerId() != null
                            ? e.getCustomerId()
                            : UUID.randomUUID(),
                    e.getCustomerEmail(),
                    e.getTrackingNumber()
            );
        }
    }
}