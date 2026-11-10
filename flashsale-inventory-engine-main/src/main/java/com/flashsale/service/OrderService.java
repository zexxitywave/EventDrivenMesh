package com.flashsale.service;

import com.flashsale.entity.Order;
import com.flashsale.entity.OrderStatus;

import java.util.List;

public interface OrderService {

    Order getOrderById(Long orderId);

    List<Order> getOrdersByUserId(Long userId);

    Order updateOrderStatus(Long orderId, OrderStatus status);

    void cancelOrder(Long orderId);
}
