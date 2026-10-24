package com.hacisimsek.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class UpdateCartItemRequest {

    @NotNull
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    public UpdateCartItemRequest() {}

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
