package com.hacisimsek.order.saga.orchestrator;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.hacisimsek.common.logging.LogPublisher;
import com.hacisimsek.order.model.Order;
import com.hacisimsek.order.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {

    private static final String SERVICE_NAME = "order-service";

    private final OrderService orderService;
    private final LogPublisher logPublisher;

    public void onInventoryReserved(UUID orderId, UUID correlationId, Order.OrderStatus previousStatus) {
        log.info("[Orchestrator] onInventoryReserved called for order={}", orderId);
        try {
            orderService.updateOrderStatus(orderId, Order.OrderStatus.INVENTORY_RESERVED, correlationId, previousStatus);
            log.info("[Orchestrator] set INVENTORY_RESERVED OK for order={}", orderId);
            orderService.updateOrderStatus(orderId, Order.OrderStatus.PAYMENT_PROCESSING, correlationId,
                    Order.OrderStatus.INVENTORY_RESERVED);
            log.info("[Orchestrator] set PAYMENT_PROCESSING OK for order={}", orderId);
        } catch (Exception e) {
            log.error("[Orchestrator] FAILED onInventoryReserved for order={} — {}: {}",
                    orderId, e.getClass().getName(), e.getMessage(), e);
        }
    }

    public void onPaymentCompleted(UUID orderId, UUID correlationId, UUID paymentId, Order.OrderStatus previousStatus) {
        log.info("[Orchestrator] onPaymentCompleted order={}", orderId);
        try {
            orderService.updateOrderStatus(orderId, Order.OrderStatus.PAYMENT_COMPLETED, correlationId, previousStatus);
        } catch (Exception e) {
            log.error("[Orchestrator] FAILED onPaymentCompleted order={}: {}", orderId, e.getMessage(), e);
        }
    }

    public void onShipmentCreated(UUID orderId, UUID correlationId, String trackingNumber, Order.OrderStatus previousStatus) {
        log.info("[Orchestrator] onShipmentCreated order={}", orderId);
        try {
            orderService.updateOrderStatus(orderId, Order.OrderStatus.SHIPPED, correlationId, previousStatus);
        } catch (Exception e) {
            log.error("[Orchestrator] FAILED onShipmentCreated order={}: {}", orderId, e.getMessage(), e);
        }
    }

    public void onInventoryFailed(UUID orderId, UUID correlationId, String reason, Order.OrderStatus previousStatus) {
        log.warn("[Orchestrator] onInventoryFailed order={} reason={}", orderId, reason);
        try {
            orderService.updateOrderStatus(orderId, Order.OrderStatus.CANCELLED, correlationId, previousStatus);
        } catch (Exception e) {
            log.error("[Orchestrator] FAILED onInventoryFailed order={}: {}", orderId, e.getMessage(), e);
        }
    }

    public void onPaymentFailed(UUID orderId, UUID correlationId, String reason, Order.OrderStatus previousStatus) {
        log.warn("[Orchestrator] onPaymentFailed order={} reason={}", orderId, reason);
        try {
            orderService.updateOrderStatus(orderId, Order.OrderStatus.CANCELLED, correlationId, previousStatus);
        } catch (Exception e) {
            log.error("[Orchestrator] FAILED onPaymentFailed order={}: {}", orderId, e.getMessage(), e);
        }
    }

    public void onShipmentFailed(UUID orderId, UUID correlationId, String reason, Order.OrderStatus previousStatus) {
        log.warn("[Orchestrator] onShipmentFailed order={} reason={}", orderId, reason);
        try {
            orderService.updateOrderStatus(orderId, Order.OrderStatus.CANCELLED, correlationId, previousStatus);
        } catch (Exception e) {
            log.error("[Orchestrator] FAILED onShipmentFailed order={}: {}", orderId, e.getMessage(), e);
        }
    }

    /**
     * Compensation event received on compensation-events (BEFORE the ORDER_CANCELLED
     * event) — payment was refunded after a failure. Recorded on the event log only;
     * the order's own status remains untouched until onShipmentFailed/onPaymentFailed
     * resolves it to CANCELLED.
     */
    public void onPaymentRefunded(UUID orderId, UUID correlationId, String reason) {
        log.info("[Orchestrator] onPaymentRefunded order={} reason={}", orderId, reason);
        orderService.recordCompensationEvent(orderId, correlationId,
                "PAYMENT_REFUNDED", "PAYMENT_COMPLETED", "REFUNDED",
                "Compensation: " + reason);
    }

    /**
     * Compensation event received on compensation-events — reserved stock was released
     * after a failure. Recorded on the event log only.
     */
    public void onStockReleased(UUID orderId, UUID correlationId, String reason) {
        log.info("[Orchestrator] onStockReleased order={} reason={}", orderId, reason);
        orderService.recordCompensationEvent(orderId, correlationId,
                "INVENTORY_RELEASED", "INVENTORY_RESERVED", "RELEASED",
                "Compensation: " + reason);
    }
}