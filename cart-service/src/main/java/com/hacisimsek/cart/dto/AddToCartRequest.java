package com.hacisimsek.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class AddToCartRequest {

    @NotNull(message = "productId is required")
    private UUID productId;


    @NotNull
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    public AddToCartRequest() {}

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
