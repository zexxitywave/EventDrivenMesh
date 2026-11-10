package com.flashsale.service;

import com.flashsale.entity.Inventory;

public interface InventoryService {

    Inventory createInventory(Long productId, Integer quantity);

    Inventory getInventory(Long productId);

    Inventory addStock(Long productId, Integer quantity);

    Inventory reserveStock(Long productId, Integer quantity);

    Inventory reserveStockPessimistic(Long productId, Integer quantity);

    Inventory reserveStockNoLocking(Long productId, Integer quantity);

    Inventory reserveStockRedis(Long productId, Integer quantity);

    Inventory releaseStock(Long productId, Integer quantity);

    Inventory resetStock(Long productId, Integer quantity);
}
