package com.hacisimsek.inventory.service;

import com.hacisimsek.inventory.dto.InventoryItemRequest;
import com.hacisimsek.inventory.dto.InventoryItemResponse;
import com.hacisimsek.inventory.dto.RestockRequest;

import java.util.List;
import java.util.UUID;

public interface InventoryItemService {

    InventoryItemResponse createInventoryItem(InventoryItemRequest request);

    InventoryItemResponse getInventoryItemById(UUID id);

    InventoryItemResponse getInventoryItemByProductId(UUID productId);

    List<InventoryItemResponse> getAllInventoryItems();

    InventoryItemResponse updateInventoryItem(UUID id, InventoryItemRequest request);

    InventoryItemResponse restockItem(UUID id, RestockRequest request);

    void deleteInventoryItem(UUID id);

    boolean checkAvailability(UUID productId, Integer quantity);

    List<InventoryItemResponse> getLowStockItems();
}
