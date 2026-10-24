package com.hacisimsek.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequest {
    @NotNull
    private UUID customerId;

    private String customerEmail; // optional — propagated through saga when provided

    private String shippingAddress; // real delivery address — geocoded by shipping-service

    @NotEmpty
    private List<OrderItemRequest> items;
}