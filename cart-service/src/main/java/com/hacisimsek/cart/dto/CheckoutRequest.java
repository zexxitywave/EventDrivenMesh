package com.hacisimsek.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CheckoutRequest {
    private UUID customerId;
    private String customerEmail;
    private List<CheckoutItemRequest> items;
}
