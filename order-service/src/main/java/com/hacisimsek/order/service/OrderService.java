package com.hacisimsek.order.service;

import com.hacisimsek.order.dto.OrderRequest;
import com.hacisimsek.order.dto.OrderResponse;
import com.hacisimsek.order.model.Order;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    OrderResponse createOrder(OrderRequest orderRequest);
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
}