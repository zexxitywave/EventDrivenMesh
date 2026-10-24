package com.hacisimsek.inventory.controller;

import com.hacisimsek.inventory.dto.InventoryItemRequest;
import com.hacisimsek.inventory.dto.InventoryItemResponse;
import com.hacisimsek.inventory.dto.RestockRequest;
import com.hacisimsek.inventory.service.InventoryItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryItemService inventoryItemService;

    /** Create an inventory record for a product */
    @PostMapping
    public ResponseEntity<InventoryItemResponse> createInventoryItem(
            @Valid @RequestBody InventoryItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryItemService.createInventoryItem(request));
    }

    /** Get by inventory record ID */
    @GetMapping("/{id}")
    public ResponseEntity<InventoryItemResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(inventoryItemService.getInventoryItemById(id));
    }

    /** Get by product UUID (most useful for other services) */
    @GetMapping("/product/{productId}")
    public ResponseEntity<InventoryItemResponse> getByProductId(@PathVariable UUID productId) {
        return ResponseEntity.ok(inventoryItemService.getInventoryItemByProductId(productId));
    }

    /** List all inventory records */
    @GetMapping
    public ResponseEntity<List<InventoryItemResponse>> getAll() {
        return ResponseEntity.ok(inventoryItemService.getAllInventoryItems());
    }

    /** Update inventory record (quantity, location, threshold) */
    @PutMapping("/{id}")
    public ResponseEntity<InventoryItemResponse> updateInventoryItem(
            @PathVariable UUID id,
            @Valid @RequestBody InventoryItemRequest request) {
        return ResponseEntity.ok(inventoryItemService.updateInventoryItem(id, request));
    }

    /** Restock â€” add units to an existing inventory record */
    @PostMapping("/{id}/restock")
    public ResponseEntity<InventoryItemResponse> restock(
            @PathVariable UUID id,
            @Valid @RequestBody RestockRequest request) {
        return ResponseEntity.ok(inventoryItemService.restockItem(id, request));
    }

    /** Delete an inventory record */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInventoryItem(@PathVariable UUID id) {
        inventoryItemService.deleteInventoryItem(id);
        return ResponseEntity.noContent().build();
    }

    /** Check if a product has sufficient stock â€” used by order-service before placing an order */
    @GetMapping("/check")
    public ResponseEntity<Boolean> checkAvailability(
            @RequestParam UUID productId,
            @RequestParam Integer quantity) {
        return ResponseEntity.ok(inventoryItemService.checkAvailability(productId, quantity));
    }

    /** Get all items currently in LOW_STOCK status */
    @GetMapping("/low-stock")
    public ResponseEntity<List<InventoryItemResponse>> getLowStockItems() {
        return ResponseEntity.ok(inventoryItemService.getLowStockItems());
    }
}
