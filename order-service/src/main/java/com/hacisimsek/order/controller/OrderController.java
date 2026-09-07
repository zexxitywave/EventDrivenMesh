package com.hacisimsek.order.controller;

import com.hacisimsek.order.dto.OrderRequest;
import com.hacisimsek.order.dto.OrderResponse;
import com.hacisimsek.order.eventsourcing.OrderEvent;
import com.hacisimsek.order.eventsourcing.OrderEventService;
import com.hacisimsek.order.service.OrderService;
import com.hacisimsek.order.sse.OrderStatusEmitter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management and real-time status tracking")
public class OrderController {

    private final OrderService orderService;
    private final OrderStatusEmitter orderStatusEmitter;
    private final OrderEventService orderEventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new order", description = "Starts the order saga (inventory -> payment -> shipping)")
    public OrderResponse createOrder(@Valid @RequestBody OrderRequest orderRequest) {
        return orderService.createOrder(orderRequest);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID")
    public OrderResponse getOrderById(@PathVariable UUID orderId) {
        return orderService.getOrderById(orderId);
    }

    @GetMapping
    @Operation(summary = "Get all orders")
    public List<OrderResponse> getAllOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get orders by customer ID")
    public List<OrderResponse> getOrdersByCustomerId(@PathVariable UUID customerId) {
        return orderService.getOrdersByCustomerId(customerId);
    }

    /**
     * Event Sourcing audit trail - full immutable history of one order.
     */
    @GetMapping("/{orderId}/history")
    @Operation(
        summary = "Get order event history",
        description = "Returns the full immutable event log for an order (Event Sourcing audit trail)"
    )
    public List<OrderEvent> getOrderHistory(@PathVariable UUID orderId) {
        return orderEventService.getHistory(orderId);
    }

    /**
     * Get all events tied to a saga correlation ID.
     * Use this to trace the entire saga across all services.
     * Example: GET /api/v1/orders/correlation/82e5adf2-dc86-4d09-b42f-e62ca9f7c129
     */
    @GetMapping("/correlation/{correlationId}")
    @Operation(
        summary = "Get all saga events by correlation ID",
        description = "Returns all order events tied to a saga correlation ID — traces the full saga from order creation to final state"
    )
    public List<OrderEvent> getEventsByCorrelationId(@PathVariable UUID correlationId) {
        return orderEventService.getByCorrelationId(correlationId);
    }

    /**
     * SSE endpoint - streams real-time order status updates to the client.
     *
     * Usage (JavaScript):
     * const es = new EventSource('/api/v1/orders/{orderId}/status-stream');
     * es.addEventListener('status-update', e => console.log(JSON.parse(e.data)));
     * es.addEventListener('complete', () => es.close());
     *
     * Events emitted:
     *   connected     - immediately on subscription
     *   status-update - on every saga step (INVENTORY_RESERVED, PAYMENT_COMPLETED, SHIPPED, etc.)
     *   complete      - when the order reaches a terminal state (stream then closes)
     */
    @GetMapping(value = "/{orderId}/status-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "Stream real-time order status via SSE",
        description = "Opens a Server-Sent Events stream that pushes status updates as the saga progresses"
    )
    public SseEmitter streamOrderStatus(
            @Parameter(description = "Order ID to subscribe to") @PathVariable UUID orderId) {
        return orderStatusEmitter.subscribe(orderId);
    }
}
