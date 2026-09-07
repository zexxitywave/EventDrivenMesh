package com.hacisimsek.order.saga.orchestrator;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.hacisimsek.common.logging.LogPublisher;
import com.hacisimsek.order.model.Order;
import com.hacisimsek.order.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Central Saga Orchestrator — drives the order lifecycle.
 *
 * Directly updates order status on each saga outcome.
 * No state machine dependency — keeps it simple and crash-safe.
 *
 * Compensation chain on failure:
 *   InventoryFailed  → CANCELLED  (no rollback needed, nothing taken)
 *   PaymentFailed    → CANCELLED  (inventory released by InventorySagaHandler)
 *   ShipmentFailed   → FAILED     (inventory released + auto-refund by PaymentSagaHandler)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {

    private static final String SERVICE_NAME = "order-service";

    private final OrderService orderService;
    private final LogPublisher logPublisher;

    // ── Forward flow ──────────────────────────────────────────────────────────

    public void onInventoryReserved(UUID orderId, UUID correlationId) {
        updateStatus(orderId, correlationId, Order.OrderStatus.INVENTORY_RESERVED,
                "inventory-service", "Inventory reserved");
    }

    public void onPaymentCompleted(UUID orderId, UUID correlationId, UUID paymentId) {
        updateStatus(orderId, correlationId, Order.OrderStatus.PAYMENT_COMPLETED,
                "payment-service", "Payment completed — paymentId=" + paymentId);
    }

    public void onShipmentCreated(UUID orderId, UUID correlationId, String trackingNumber) {
        updateStatus(orderId, correlationId, Order.OrderStatus.SHIPPED,
                "shipping-service", "Order shipped — tracking=" + trackingNumber);
    }

    // ── Compensation ──────────────────────────────────────────────────────────

    public void onInventoryFailed(UUID orderId, UUID correlationId, String reason) {
        // No money taken, no stock reserved — simply cancel
        updateStatus(orderId, correlationId, Order.OrderStatus.CANCELLED,
                "inventory-service", "Inventory failed: " + reason);
        log.warn("[Saga] Order {} CANCELLED — inventory unavailable: {}", orderId, reason);
    }

    public void onPaymentFailed(UUID orderId, UUID correlationId, String reason) {
        // Payment never captured — cancel the order
        // Inventory is automatically released by InventorySagaHandler (listens to payment-events)
        updateStatus(orderId, correlationId, Order.OrderStatus.CANCELLED,
                "payment-service", "Payment failed: " + reason);
        log.warn("[Saga] Order {} CANCELLED — payment failed: {}", orderId, reason);
    }

    public void onShipmentFailed(UUID orderId, UUID correlationId, String reason) {
        // Payment already captured — mark FAILED (not CANCELLED)
        // Auto-refund triggered by PaymentSagaHandler (listens to shipping-events)
        // Inventory released by InventorySagaHandler (listens to shipping-events)
        updateStatus(orderId, correlationId, Order.OrderStatus.FAILED,
                "shipping-service", "Shipment failed: " + reason);
        log.warn("[Saga] Order {} FAILED — shipment failed, auto-refund triggered: {}", orderId, reason);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void updateStatus(UUID orderId, UUID correlationId,
                               Order.OrderStatus newStatus,
                               String triggeredBy, String details) {
        try {
            Order.OrderStatus prev = orderService.getOrderById(orderId).getStatus();
            orderService.updateOrderStatus(orderId, newStatus);

            log.info("[Saga] Order {} | {} → {} | by={}",
                    orderId, prev, newStatus, triggeredBy);

            logPublisher.info(SERVICE_NAME,
                    correlationId != null ? correlationId.toString() : null,
                    "[Saga] " + details,
                    Map.of(
                            "orderId", orderId.toString(),
                            "from", prev.name(),
                            "to", newStatus.name(),
                            "triggeredBy", triggeredBy
                    ));
        } catch (Exception e) {
            log.error("[Saga] Failed to update order {} to {}: {}", orderId, newStatus, e.getMessage());
        }
    }
}
