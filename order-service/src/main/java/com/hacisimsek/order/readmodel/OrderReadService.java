package com.hacisimsek.order.readmodel;

import java.util.List;
import java.util.UUID;

public interface OrderReadService {

    OrderReadResponse getById(UUID orderId);

    List<OrderReadResponse> getAll();

    List<OrderReadResponse> getByCustomerId(UUID customerId);
}