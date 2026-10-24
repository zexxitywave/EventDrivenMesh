package com.hacisimsek.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RestockRequest {

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "Restock quantity must be at least 1")
    private Integer quantity;

    private String warehouseLocation;
}
