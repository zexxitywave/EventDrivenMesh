package com.hacisimsek.inventory.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "inventory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {

    @Id
    private UUID id;

    /**
     * References the product UUID in product-service.
     * This is the canonical product identifier used across all services.
     */
    private UUID productId;

    /** Human-readable name, synced from product-service if needed */
    private String name;

    private String description;

    /** Units currently available for purchase */
    private Integer availableQuantity;

    /** Units currently held for pending orders */
    private Integer reservedQuantity;

    /** Warehouse or storage location identifier e.g. "WH-A1", "SHELF-B3" */
    private String warehouseLocation;

    @Builder.Default
    private InventoryStatus status = InventoryStatus.IN_STOCK;

    /**
     * When availableQuantity falls below this, a low-stock Kafka event is published.
     * Default: 10 units.
     */
    @Builder.Default
    private Integer lowStockThreshold = 10;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
