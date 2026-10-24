package com.hacisimsek.order.readmodel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.common.event.inventory.InventoryReservationFailedEvent;
import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.inventory.StockReleasedEvent;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.event.payment.PaymentRefundedEvent;
import com.hacisimsek.common.event.shipping.ShipmentFailedEvent;
import com.hacisimsek.common.event.shipping.ShipmentProcessedEvent;
import com.hacisimsek.order.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * CQRS read-side projection.
 *
 * <p>Consumes the exact same saga events as {@code OrderSagaHandler} but with its
 * own consumer group ({@code order-read-model-group}) so it never steals partitions
 * from the saga listeners, and blindly projects the latest state into
 * {@code order_reads}. No business logic — only mirroring.
 *
 * <p>Status transitions deliberately match the write side's authoritative JDBC
 * transitions in OrderSagaHandler:
 * <pre>
 *   OrderCreatedEvent          -> INVENTORY_CHECKING   (matches createOrder)
 *   InventoryReservedEvent     -> PAYMENT_PROCESSING
 *   InventoryReservationFailed -> CANCELLED
 *   PaymentProcessedEvent      -> PAYMENT_COMPLETED
 *   PaymentFailedEvent         -> CANCELLED
 *   ShipmentProcessedEvent     -> SHIPPED
 *   ShipmentFailedEvent        -> CANCELLED
 *   PaymentRefundedEvent       -> refunded = true      (no status change)
 *   StockReleasedEvent         -> stockReleased = true (no status change)
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderReadProjection {

    private final OrderReadModelRepository readModelRepository;
    private final ObjectMapper objectMapper;

    private static final String ORDER_CREATED           = OrderCreatedEvent.class.getName();
    private static final String INVENTORY_RESERVED      = InventoryReservedEvent.class.getName();
    private static final String INVENTORY_FAILED        = InventoryReservationFailedEvent.class.getName();
    private static final String PAYMENT_PROCESSED       = PaymentProcessedEvent.class.getName();
    private static final String PAYMENT_FAILED          = PaymentFailedEvent.class.getName();
    private static final String SHIPMENT_PROCESSED      = ShipmentProcessedEvent.class.getName();
    private static final String SHIPMENT_FAILED         = ShipmentFailedEvent.class.getName();
    private static final String PAYMENT_REFUNDED        = PaymentRefundedEvent.class.getName();
    private static final String STOCK_RELEASED          = StockReleasedEvent.class.getName();

    @KafkaListener(topics = {"order-events", "inventory-events", "payment-events",
            "shipping-events", "compensation-events"},
            groupId = "order-read-model-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void project(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        if (event == null) {
            return;
        }
        String type = event.getClass().getName();
        if (type == null) {
            return;
        }

        OrderEventPayload payload = objectMapper.convertValue(event, OrderEventPayload.class);
        if (payload.getOrderId() == null) {
            log.warn("[OrderReadProjection] Dropping event {} without orderId", type);
            return;
        }

        try {
            apply(payload, payload.getOrderId(), type);
            log.info("[OrderReadProjection] Projected {} for order={} -> {}",
                    simpleName(type), payload.getOrderId(),
                    simpleName(type));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to project event type=" + type, ex);
        }
    }

    private void apply(OrderEventPayload payload, java.util.UUID orderId, String type)
            throws JsonProcessingException {

        java.util.Optional<OrderReadModel> existing = readModelRepository.findById(orderId);
        boolean freshlyCreated = existing.isEmpty();
        OrderReadModel readModel = existing.orElseGet(() -> {
            OrderReadModel fresh = OrderReadModel.builder()
                    .orderId(orderId)
                    .customerId(payload.getCustomerId())
                    .customerEmail(payload.getCustomerEmail())
                    .correlationId(payload.getCorrelationId())
                    .status(Order.OrderStatus.PENDING)
                    .itemCount(0)
                    .build();
            if (payload.getTimestamp() != null) {
                fresh.setCreatedAt(payload.getTimestamp());
            }
            return fresh;
        });

        // Creation data — only present on OrderCreatedEvent.
        if (ORDER_CREATED.equals(type)) {
            if (payload.getCustomerId() != null) readModel.setCustomerId(payload.getCustomerId());
            if (payload.getCustomerEmail() != null) readModel.setCustomerEmail(payload.getCustomerEmail());
            if (payload.getCorrelationId() != null) readModel.setCorrelationId(payload.getCorrelationId());
            if (payload.getTotalAmount() != null) readModel.setTotalAmount(payload.getTotalAmount());
            if (payload.getItems() != null) {
                readModel.setItemCount(payload.getItems().size());
                readModel.setItemsJson(objectMapper.writeValueAsString(payload.getItems()));
            }
            if (readModel.getCreatedAt() == null && payload.getTimestamp() != null) {
                readModel.setCreatedAt(payload.getTimestamp());
            }
            // The write side leaves the order at INVENTORY_CHECKING after creation.
            // Only promote freshly-projected (or still-PENDING) rows; never regress
            // an already-advanced status such as PAYMENT_PROCESSING.
            if (freshlyCreated || readModel.getStatus() == null
                    || readModel.getStatus() == Order.OrderStatus.PENDING) {
                readModel.setStatus(Order.OrderStatus.INVENTORY_CHECKING);
            }
        }

        // Status transitions mirror the saga's authoritative JDBC updates.
        String status = null;
        if (INVENTORY_RESERVED.equals(type)) {
            status = "PAYMENT_PROCESSING";
        } else if (INVENTORY_FAILED.equals(type)
                || PAYMENT_FAILED.equals(type)
                || SHIPMENT_FAILED.equals(type)) {
            status = "CANCELLED";
        } else if (PAYMENT_PROCESSED.equals(type)) {
            status = "PAYMENT_COMPLETED";
        } else if (SHIPMENT_PROCESSED.equals(type)) {
            status = "SHIPPED";
        }
        if (status != null) {
            readModel.setStatus(Order.OrderStatus.valueOf(status));
        }

        // Compensation flags — no status change, the order stays cancelled.
        if (PAYMENT_REFUNDED.equals(type)) {
            readModel.setRefunded(true);
        }
        if (STOCK_RELEASED.equals(type)) {
            readModel.setStockReleased(true);
        }

        readModel.setLastEvent(simpleName(type));
        readModel.setLastEventAt(payload.getTimestamp() != null ? payload.getTimestamp() : Instant.now());

        readModelRepository.save(readModel);
    }

    private String simpleName(String fqcn) {
        int idx = fqcn.lastIndexOf('.');
        return idx >= 0 ? fqcn.substring(idx + 1) : fqcn;
    }
}