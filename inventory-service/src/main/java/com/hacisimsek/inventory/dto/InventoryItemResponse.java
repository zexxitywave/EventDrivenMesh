package com.hacisimsek.inventory.dto;

import com.hacisimsek.inventory.model.InventoryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemResponse {
    private UUID id;
    private UUID productId;
    private String name;
    private String description;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private String warehouseLocation;
    private InventoryStatus status;
    private Integer lowStockThreshold;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
