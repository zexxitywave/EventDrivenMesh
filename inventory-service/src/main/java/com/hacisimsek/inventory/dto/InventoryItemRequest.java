package com.hacisimsek.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemRequest {

    /** Must match the product UUID in product-service */
    @NotNull(message = "productId is required")
    private UUID productId;

    private String name;

    private String description;

    @NotNull(message = "quantity is required")
    @Min(value = 0, message = "quantity cannot be negative")
    private Integer quantity;

    private String warehouseLocation;

    @Min(value = 0, message = "lowStockThreshold cannot be negative")
    private Integer lowStockThreshold;
}
