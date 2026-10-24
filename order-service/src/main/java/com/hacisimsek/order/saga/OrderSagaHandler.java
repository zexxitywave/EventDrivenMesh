package com.hacisimsek.order.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.common.event.inventory.InventoryReservationFailedEvent;
import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.inventory.StockReleasedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.event.payment.PaymentRefundedEvent;
import com.hacisimsek.common.event.shipping.ShipmentFailedEvent;
import com.hacisimsek.common.event.shipping.ShipmentProcessedEvent;
import com.hacisimsek.order.model.Order;
import com.hacisimsek.order.saga.orchestrator.OrderSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Kafka listeners for the Order Saga.
 *
 * Uses class-name string comparison (not instanceof) to avoid classloader issues.
 * Uses JdbcTemplate for the status update to bypass any JPA/EntityManager
 * thread-binding issues on the Kafka consumer thread.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaHandler {

    private final OrderSagaOrchestrator orchestrator;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    private static final String INVENTORY_RESERVED = InventoryReservedEvent.class.getName();
    private static final String INVENTORY_FAILED    = InventoryReservationFailedEvent.class.getName();
    private static final String PAYMENT_PROCESSED   = PaymentProcessedEvent.class.getName();
    private static final String PAYMENT_FAILED      = PaymentFailedEvent.class.getName();
    private static final String SHIPMENT_PROCESSED  = ShipmentProcessedEvent.class.getName();
    private static final String SHIPMENT_FAILED_CN  = ShipmentFailedEvent.class.getName();

    private static final String PAYMENT_REFUNDED_CN = PaymentRefundedEvent.class.getName();
    private static final String STOCK_RELEASED_CN   = StockReleasedEvent.class.getName();

    /**
     * Reads the CURRENT status from the DB without any JPA/EntityManager thread
     * involvement — captured BEFORE the direct JDBC update so the event-log
     * previousStatus reflects the true preceding state, not the post-update one.
     */
    private Order.OrderStatus queryStatus(UUID orderId) {
        String current;
        try {
            current = jdbcTemplate.queryForObject(
                    "SELECT status FROM orders WHERE id = ?", String.class, orderId);
        } catch (Exception e) {
            log.warn("[SagaHandler] Could not read status for order {}: {}", orderId, e.getMessage());
            return null;
        }
        if (current == null) {
            return null;
        }
        try {
            return Order.OrderStatus.valueOf(current);
        } catch (IllegalArgumentException e) {
            log.warn("[SagaHandler] Unknown status '{}' for order {}", current, orderId);
            return null;
        }
    }

    @KafkaListener(topics = "inventory-events", groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleInventoryEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        if (event == null) return;
        String type = event.getClass().getName();
        log.info("[SagaHandler] inventory-events type={}", type);

        try {
            if (INVENTORY_RESERVED.equals(type)) {
                InventoryReservedEvent e = objectMapper.convertValue(event, InventoryReservedEvent.class);
                UUID orderId = e.getOrderId();
                log.info("[SagaHandler] InventoryReserved for order={} — updating DB directly", orderId);

                Order.OrderStatus previousStatus = queryStatus(orderId);

                // Direct JDBC update — bypasses JPA/EntityManager thread issues completely
                int rows = jdbcTemplate.update(
                    "UPDATE orders SET status = 'PAYMENT_PROCESSING', last_modified_at = NOW() WHERE id = ?",
                    orderId);
                log.info("[SagaHandler] JDBC UPDATE affected {} rows for order={}", rows, orderId);

                if (rows > 0) {
                    // Also call orchestrator for event sourcing / SSE / logging
                    orchestrator.onInventoryReserved(orderId, e.getCorrelationId(), previousStatus);
                }

            } else if (INVENTORY_FAILED.equals(type)) {
                InventoryReservationFailedEvent e = objectMapper.convertValue(event, InventoryReservationFailedEvent.class);
                UUID orderId = e.getOrderId();
                log.info("[SagaHandler] InventoryFailed for order={}", orderId);
                Order.OrderStatus previousStatus = queryStatus(orderId);
                jdbcTemplate.update(
                    "UPDATE orders SET status = 'CANCELLED', last_modified_at = NOW() WHERE id = ?",
                    orderId);
                orchestrator.onInventoryFailed(orderId, e.getCorrelationId(),
                        e.getReason() != null ? e.getReason() : "unknown", previousStatus);
            } else {
                log.warn("[SagaHandler] Unhandled inventory event type={}", type);
            }
        } catch (Exception ex) {
            // Rethrow so Spring Kafka's DefaultErrorHandler retries the record
            // instead of silently committing the offset — otherwise the order can
            // stay stuck in INVENTORY_CHECKING forever with no visible trace.
            log.error("[SagaHandler] ERROR handling inventory event type={}: {}", type, ex.getMessage(), ex);
            throw new IllegalStateException("Failed to process inventory event type=" + type, ex);
        }
    }

    @KafkaListener(topics = "payment-events", groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        if (event == null) return;
        String type = event.getClass().getName();
        log.info("[SagaHandler] payment-events type={}", type);

        try {
            if (PAYMENT_PROCESSED.equals(type)) {
                PaymentProcessedEvent e = objectMapper.convertValue(event, PaymentProcessedEvent.class);
                UUID orderId = e.getOrderId();
                Order.OrderStatus previousStatus = queryStatus(orderId);
                jdbcTemplate.update(
                    "UPDATE orders SET status = 'PAYMENT_COMPLETED', last_modified_at = NOW() WHERE id = ?",
                    orderId);
                orchestrator.onPaymentCompleted(orderId, e.getCorrelationId(), e.getPaymentId(), previousStatus);

            } else if (PAYMENT_FAILED.equals(type)) {
                PaymentFailedEvent e = objectMapper.convertValue(event, PaymentFailedEvent.class);
                UUID orderId = e.getOrderId();
                Order.OrderStatus previousStatus = queryStatus(orderId);
                jdbcTemplate.update(
                    "UPDATE orders SET status = 'CANCELLED', last_modified_at = NOW() WHERE id = ?",
                    orderId);
                orchestrator.onPaymentFailed(orderId, e.getCorrelationId(),
                        e.getReason() != null ? e.getReason() : "unknown", previousStatus);
            } else {
                log.warn("[SagaHandler] Unhandled payment event type={}", type);
            }
        } catch (Exception ex) {
            log.error("[SagaHandler] ERROR handling payment event: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Failed to process payment event type=" + type, ex);
        }
    }

    @KafkaListener(topics = "shipping-events", groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleShippingEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        if (event == null) return;
        String type = event.getClass().getName();
        log.info("[SagaHandler] shipping-events type={}", type);

        try {
            if (SHIPMENT_PROCESSED.equals(type)) {
                ShipmentProcessedEvent e = objectMapper.convertValue(event, ShipmentProcessedEvent.class);
                UUID orderId = e.getOrderId();
                Order.OrderStatus previousStatus = queryStatus(orderId);
                jdbcTemplate.update(
                    "UPDATE orders SET status = 'SHIPPED', last_modified_at = NOW() WHERE id = ?",
                    orderId);
                orchestrator.onShipmentCreated(orderId, e.getCorrelationId(), e.getTrackingNumber(), previousStatus);

            } else if (SHIPMENT_FAILED_CN.equals(type)) {
                ShipmentFailedEvent e = objectMapper.convertValue(event, ShipmentFailedEvent.class);
                UUID orderId = e.getOrderId();
                Order.OrderStatus previousStatus = queryStatus(orderId);
                jdbcTemplate.update(
                    "UPDATE orders SET status = 'CANCELLED', last_modified_at = NOW() WHERE id = ?",
                    orderId);
                orchestrator.onShipmentFailed(orderId, e.getCorrelationId(),
                        e.getReason() != null ? e.getReason() : "unknown", previousStatus);
            } else {
                log.warn("[SagaHandler] Unhandled shipping event type={}", type);
            }
        } catch (Exception ex) {
            log.error("[SagaHandler] ERROR handling shipping event: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Failed to process shipping event type=" + type, ex);
        }
    }

    @KafkaListener(topics = "compensation-events", groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleCompensationEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        if (event == null) return;
        String type = event.getClass().getName();
        log.info("[SagaHandler] compensation-events type={}", type);

        try {
            if (PAYMENT_REFUNDED_CN.equals(type)) {
                PaymentRefundedEvent e = objectMapper.convertValue(event, PaymentRefundedEvent.class);
                orchestrator.onPaymentRefunded(e.getOrderId(), e.getCorrelationId(),
                        e.getReason() != null ? e.getReason() : "payment refunded after saga failure");
            } else if (STOCK_RELEASED_CN.equals(type)) {
                StockReleasedEvent e = objectMapper.convertValue(event, StockReleasedEvent.class);
                orchestrator.onStockReleased(e.getOrderId(), e.getCorrelationId(),
                        e.getReason() != null ? e.getReason() : "reserved stock released after saga failure");
            } else {
                log.warn("[SagaHandler] Unhandled compensation event type={}", type);
            }
        } catch (Exception ex) {
            log.error("[SagaHandler] ERROR handling compensation event: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Failed to process compensation event type=" + type, ex);
        }
    }
}