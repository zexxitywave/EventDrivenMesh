package com.hacisimsek.order.saga;

import java.util.Map;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.hacisimsek.common.event.inventory.InventoryReservationFailedEvent;
import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.event.shipping.ShipmentFailedEvent;
import com.hacisimsek.common.event.shipping.ShipmentProcessedEvent;
import com.hacisimsek.common.logging.LogPublisher;
import com.hacisimsek.order.model.Order;
import com.hacisimsek.order.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaHandler {

    private static final String SERVICE_NAME = "order-service";

    private final OrderService orderService;
    private final LogPublisher logPublisher;

    @KafkaListener(topics = "inventory-events", groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleInventoryEvents(Object event) {
        log.info("Received inventory event: {}", event.getClass().getSimpleName());

        if (event instanceof InventoryReservedEvent reservedEvent) {
            orderService.updateOrderStatus(reservedEvent.getOrderId(), Order.OrderStatus.INVENTORY_RESERVED);
            log.info("Inventory reserved for order: {}", reservedEvent.getOrderId());
            logPublisher.info(SERVICE_NAME,
                    reservedEvent.getCorrelationId() != null ? reservedEvent.getCorrelationId().toString() : null,
                    "Inventory reserved for order: " + reservedEvent.getOrderId(),
                    Map.of("orderId", reservedEvent.getOrderId().toString(), "status", "INVENTORY_RESERVED"));

        } else if (event instanceof InventoryReservationFailedEvent failedEvent) {
            orderService.updateOrderStatus(failedEvent.getOrderId(), Order.OrderStatus.CANCELLED);
            log.error("Inventory reservation failed for order: {}, reason: {}",
                    failedEvent.getOrderId(), failedEvent.getReason());
            logPublisher.error(SERVICE_NAME,
                    failedEvent.getCorrelationId() != null ? failedEvent.getCorrelationId().toString() : null,
                    "Inventory reservation failed — order cancelled: " + failedEvent.getOrderId(),
                    Map.of("orderId", failedEvent.getOrderId().toString(),
                           "reason", failedEvent.getReason() != null ? failedEvent.getReason() : "unknown",
                           "status", "CANCELLED"));
        }
    }

    @KafkaListener(topics = "payment-events", groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentEvents(Object event) {
        log.info("Received payment event: {}", event.getClass().getSimpleName());

        if (event instanceof PaymentProcessedEvent processedEvent) {
            orderService.updateOrderStatus(processedEvent.getOrderId(), Order.OrderStatus.PAYMENT_COMPLETED);
            log.info("Payment processed for order: {}", processedEvent.getOrderId());
            logPublisher.info(SERVICE_NAME,
                    processedEvent.getCorrelationId() != null ? processedEvent.getCorrelationId().toString() : null,
                    "Payment completed for order: " + processedEvent.getOrderId(),
                    Map.of("orderId", processedEvent.getOrderId().toString(),
                           "paymentId", processedEvent.getPaymentId() != null ? processedEvent.getPaymentId().toString() : "unknown",
                           "status", "PAYMENT_COMPLETED"));

        } else if (event instanceof PaymentFailedEvent failedEvent) {
            orderService.updateOrderStatus(failedEvent.getOrderId(), Order.OrderStatus.FAILED);
            log.error("Payment failed for order: {}, reason: {}",
                    failedEvent.getOrderId(), failedEvent.getReason());
            logPublisher.error(SERVICE_NAME,
                    failedEvent.getCorrelationId() != null ? failedEvent.getCorrelationId().toString() : null,
                    "Payment failed — order marked FAILED: " + failedEvent.getOrderId(),
                    Map.of("orderId", failedEvent.getOrderId().toString(),
                           "reason", failedEvent.getReason() != null ? failedEvent.getReason() : "unknown",
                           "status", "FAILED"));
        }
    }

    @KafkaListener(topics = "shipping-events", groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleShippingEvents(Object event) {
        log.info("Received shipping event: {}", event.getClass().getSimpleName());

        if (event instanceof ShipmentProcessedEvent processedEvent) {
            orderService.updateOrderStatus(processedEvent.getOrderId(), Order.OrderStatus.SHIPPED);
            log.info("Order shipped: {}, tracking number: {}",
                    processedEvent.getOrderId(), processedEvent.getTrackingNumber());
            logPublisher.info(SERVICE_NAME,
                    processedEvent.getCorrelationId() != null ? processedEvent.getCorrelationId().toString() : null,
                    "Order shipped: " + processedEvent.getOrderId() + " | tracking: " + processedEvent.getTrackingNumber(),
                    Map.of("orderId", processedEvent.getOrderId().toString(),
                           "trackingNumber", processedEvent.getTrackingNumber() != null ? processedEvent.getTrackingNumber() : "unknown",
                           "status", "SHIPPED"));

        } else if (event instanceof ShipmentFailedEvent failedEvent) {
            orderService.updateOrderStatus(failedEvent.getOrderId(), Order.OrderStatus.FAILED);
            log.error("Shipping failed for order: {}, reason: {}",
                    failedEvent.getOrderId(), failedEvent.getReason());
            logPublisher.error(SERVICE_NAME,
                    failedEvent.getCorrelationId() != null ? failedEvent.getCorrelationId().toString() : null,
                    "Shipment failed — order marked FAILED: " + failedEvent.getOrderId(),
                    Map.of("orderId", failedEvent.getOrderId().toString(),
                           "reason", failedEvent.getReason() != null ? failedEvent.getReason() : "unknown",
                           "status", "FAILED"));
        }
    }
}