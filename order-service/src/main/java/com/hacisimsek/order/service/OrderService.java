package com.hacisimsek.order.service;

import com.hacisimsek.order.dto.OrderRequest;
import com.hacisimsek.order.dto.OrderResponse;
import com.hacisimsek.order.model.Order;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    OrderResponse createOrder(OrderRequest orderRequest);

    /**
     * Creates a batch of orders in one call — each order starts its own saga
     * (own outbox entry, own correlationId). One failing order rolls back the
     * whole batch.
     */
    List<OrderResponse> createOrders(List<OrderRequest> orderRequests);
    OrderResponse getOrderById(UUID orderId);
    List<OrderResponse> getAllOrders();
    List<OrderResponse> getOrdersByCustomerId(UUID customerId);
    void updateOrderStatus(UUID orderId, Order.OrderStatus status);
    void updateOrderStatus(UUID orderId, Order.OrderStatus status, UUID correlationId);

    /**
     * Status transition that records the given previousStatus (captured before
     * any direct DB write) on the event-log entry — keeps the saga trace truthful
     * when the handler pre-updates the orders table via JDBC.
     */
    void updateOrderStatus(UUID orderId, Order.OrderStatus status, UUID correlationId, Order.OrderStatus previousStatus);

    /**
     * Records a saga compensation event (PAYMENT_REFUNDED, INVENTORY_RELEASED)
     * on the order event log WITHOUT changing the order's own status. The
     * previousStatus reflects the prior state of the service that was
     * compensated (e.g. PAYMENT_COMPLETED for a refund, INVENTORY_RESERVED
     * for a stock release).
     */
    void recordCompensationEvent(UUID orderId, UUID correlationId,
                                 String eventType, String previousStatus,
                                 String newStatus, String details);
}