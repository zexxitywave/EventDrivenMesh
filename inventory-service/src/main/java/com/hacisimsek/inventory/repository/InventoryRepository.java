package com.hacisimsek.inventory.repository;

import com.hacisimsek.inventory.model.InventoryItem;
import com.hacisimsek.inventory.model.InventoryStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends MongoRepository<InventoryItem, UUID> {

    Optional<InventoryItem> findByProductId(UUID productId);

    List<InventoryItem> findByStatus(InventoryStatus status);

    /** Returns all items where availableQuantity <= lowStockThreshold */
    @Query("{ 'availableQuantity': { $lte: '$lowStockThreshold' }, 'status': { $ne: 'OUT_OF_STOCK' } }")
    List<InventoryItem> findLowStockItems();

    List<InventoryItem> findByWarehouseLocation(String warehouseLocation);
}
