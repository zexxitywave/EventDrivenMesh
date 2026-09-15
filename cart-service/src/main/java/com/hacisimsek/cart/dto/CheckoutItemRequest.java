package com.hacisimsek.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CheckoutItemRequest {
    private UUID productId;
    private String productName;
    private Integer quantity;
    private BigDecimal price;
}
