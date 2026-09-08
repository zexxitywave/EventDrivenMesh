package com.hacisimsek.order.controller;

import com.hacisimsek.order.dto.OrderRequest;
import com.hacisimsek.order.dto.OrderResponse;
import com.hacisimsek.order.eventsourcing.OrderEvent;
import com.hacisimsek.order.eventsourcing.OrderEventService;
import com.hacisimsek.order.model.Order;
import com.hacisimsek.order.service.OrderService;
import com.hacisimsek.order.sse.OrderStatusEmitter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Orders", description = "Order management and real-time status tracking")
public class OrderController {

    private final OrderService orderService;
    private final OrderStatusEmitter orderStatusEmitter;
    private final OrderEventService orderEventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody OrderRequest orderRequest) {
        return orderService.createOrder(orderRequest);
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrderById(@PathVariable UUID orderId) {
        return orderService.getOrderById(orderId);
    }

    @GetMapping
    public List<OrderResponse> getAllOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/customer/{customerId}")
    public List<OrderResponse> getOrdersByCustomerId(@PathVariable UUID customerId) {
        return orderService.getOrdersByCustomerId(customerId);
    }

    @GetMapping("/{orderId}/history")
    public List<OrderEvent> getOrderHistory(@PathVariable UUID orderId) {
        return orderEventService.getHistory(orderId);
    }

    @GetMapping("/correlation/{correlationId}")
    public List<OrderEvent> getEventsByCorrelationId(@PathVariable UUID correlationId) {
        return orderEventService.getByCorrelationId(correlationId);
    }

    @GetMapping(value = "/{orderId}/status-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOrderStatus(@PathVariable UUID orderId) {
        return orderStatusEmitter.subscribe(orderId);
    }

    /**
     * DEBUG endpoint — directly advance an order to a given status.
     * Used to test updateOrderStatus independently of the Kafka listener.
     * Remove before production.
     */
    @PatchMapping("/{orderId}/status/{status}")
    public ResponseEntity<OrderResponse> debugSetStatus(
            @PathVariable UUID orderId,
            @PathVariable Order.OrderStatus status) {
        log.warn("[DEBUG] Manually setting order {} to {}", orderId, status);
        try {
            orderService.updateOrderStatus(orderId, status);
            return ResponseEntity.ok(orderService.getOrderById(orderId));
        } catch (Exception e) {
            log.error("[DEBUG] updateOrderStatus threw: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}