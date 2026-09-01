package com.hacisimsek.order.saga.orchestrator;

import com.hacisimsek.common.logging.LogPublisher;
import com.hacisimsek.order.model.Order;
import com.hacisimsek.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Central Saga Orchestrator — drives the Order state machine.
 *
 * Instead of each service reacting to events independently (choreography),
 * this orchestrator:
 *  1. Maintains the authoritative state machine per order
 *  2. Receives all saga outcomes (inventory/payment/shipment results)
 *  3. Decides the next step and updates order status
 *  4. Handles compensation centrally (e.g. release inventory on payment failure)
 *
 * The Kafka listeners in OrderSagaHandler delegate to this orchestrator.
 * This keeps all saga logic in one place instead of scattered across handlers.
 *
 * State machine instances are stateless in this implementation — the current
 * state is always loaded from the database (Order.status) before processing
 * each event, which makes it crash-safe and idempotent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {

    private static final String SERVICE_NAME = "order-service";
    private static final String ORDER_ID_KEY = "orderId";
    private static final String CORRELATION_ID_KEY = "correlationId";

    private final StateMachineFactory<Order.OrderStatus, SagaEvent> stateMachineFactory;
    private final OrderService orderService;
    private final LogPublisher logPublisher;

    // ── Public API called by Kafka listeners ──────────────────────────────────

    public void onInventoryReserved(UUID orderId, UUID correlationId) {
        processEvent(orderId, correlationId, SagaEvent.INVENTORY_RESERVED,
                Order.OrderStatus.INVENTORY_RESERVED,
                "inventory-service", "Inventory reserved — initiating payment");
    }

    public void onInventoryFailed(UUID orderId, UUID correlationId, String reason) {
        processEvent(orderId, correlationId, SagaEvent.INVENTORY_FAILED,
                Order.OrderStatus.CANCELLED,
                "inventory-service", "Inventory reservation failed: " + reason);
        log.warn("[Orchestrator] Order {} CANCELLED — inventory failed: {}", orderId, reason);
    }

    public void onPaymentCompleted(UUID orderId, UUID correlationId, UUID paymentId) {
        processEvent(orderId, correlationId, SagaEvent.PAYMENT_COMPLETED,
                Order.OrderStatus.PAYMENT_COMPLETED,
                "payment-service", "Payment completed, paymentId=" + paymentId);
    }

    public void onPaymentFailed(UUID orderId, UUID correlationId, String reason) {
        processEvent(orderId, correlationId, SagaEvent.PAYMENT_FAILED,
                Order.OrderStatus.FAILED,
                "payment-service", "Payment failed: " + reason);
        // Compensation is handled by inventory-service which listens to payment-events
        // (already implemented in InventorySagaHandler)
        log.warn("[Orchestrator] Order {} FAILED — payment failed: {}", orderId, reason);
    }

    public void onShipmentCreated(UUID orderId, UUID correlationId, String trackingNumber) {
        processEvent(orderId, correlationId, SagaEvent.SHIPMENT_CREATED,
                Order.OrderStatus.SHIPPED,
                "shipping-service", "Shipment created, tracking=" + trackingNumber);
    }

    public void onShipmentFailed(UUID orderId, UUID correlationId, String reason) {
        processEvent(orderId, correlationId, SagaEvent.SHIPMENT_FAILED,
                Order.OrderStatus.FAILED,
                "shipping-service", "Shipment failed: " + reason);
        log.warn("[Orchestrator] Order {} FAILED — shipment failed: {}", orderId, reason);
    }

    // ── Core state machine processing ─────────────────────────────────────────

    private void processEvent(UUID orderId,
                               UUID correlationId,
                               SagaEvent event,
                               Order.OrderStatus targetStatus,
                               String triggeredBy,
                               String details) {
        try {
            // Load current state from DB (crash-safe: state machine is rebuilt each time)
            Order.OrderStatus currentStatus = orderService.getOrderById(orderId).getStatus();

            // Build a state machine pre-loaded at the current state
            StateMachine<Order.OrderStatus, SagaEvent> sm = buildStateMachine(orderId, currentStatus);

            // Send the event
            Message<SagaEvent> message = MessageBuilder.withPayload(event)
                    .setHeader(ORDER_ID_KEY, orderId.toString())
                    .setHeader(CORRELATION_ID_KEY, correlationId != null ? correlationId.toString() : "")
                    .build();

            sm.sendEvent(Mono.just(message)).subscribe();

            Order.OrderStatus newState = sm.getState().getId();

            // Persist the new state
            orderService.updateOrderStatus(orderId, newState);

            log.info("[Orchestrator] Order {} | event={} | {} → {}",
                    orderId, event, currentStatus, newState);

            logPublisher.info(SERVICE_NAME,
                    correlationId != null ? correlationId.toString() : null,
                    "[Orchestrator] " + details,
                    Map.of("orderId", orderId.toString(),
                           "event", event.name(),
                           "from", currentStatus.name(),
                           "to", newState.name(),
                           "triggeredBy", triggeredBy));

        } catch (Exception e) {
            log.error("[Orchestrator] Failed to process event {} for order {}: {}",
                    event, orderId, e.getMessage());
        }
    }

    /**
     * Build a StateMachine instance pre-restored to the given state.
     * Using the factory (not a singleton) ensures each order gets isolated state.
     */
    private StateMachine<Order.OrderStatus, SagaEvent> buildStateMachine(
            UUID orderId, Order.OrderStatus currentState) throws Exception {

        StateMachine<Order.OrderStatus, SagaEvent> sm =
                stateMachineFactory.getStateMachine(orderId.toString());

        sm.stopReactively().block();

        sm.getStateMachineAccessor()
                .doWithAllRegions(accessor ->
                        accessor.resetStateMachineReactively(
                                new DefaultStateMachineContext<>(currentState, null, null, null)
                        ).block()
                );

        sm.startReactively().block();
        return sm;
    }
}
