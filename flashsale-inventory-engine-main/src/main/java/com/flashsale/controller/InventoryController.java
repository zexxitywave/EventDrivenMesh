package com.flashsale.controller;

import com.flashsale.entity.Inventory;
import com.flashsale.event.InventoryReservationEvent;
import com.flashsale.service.InventoryEventProducer;
import com.flashsale.service.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryEventProducer inventoryEventProducer;

    public InventoryController(
            InventoryService inventoryService,
            InventoryEventProducer inventoryEventProducer) {

        this.inventoryService = inventoryService;
        this.inventoryEventProducer = inventoryEventProducer;
    }

    @PostMapping("/{productId}")
    public ResponseEntity<Inventory> createInventory(
            @PathVariable Long productId,
            @RequestParam Integer quantity) {

        return new ResponseEntity<>(
                inventoryService.createInventory(productId, quantity),
                HttpStatus.CREATED
        );
    }

    @GetMapping("/{productId}")
    public ResponseEntity<Inventory> getInventory(
            @PathVariable Long productId) {

        return ResponseEntity.ok(
                inventoryService.getInventory(productId)
        );
    }

    @PatchMapping("/{productId}/add")
    public ResponseEntity<Inventory> addStock(
            @PathVariable Long productId,
            @RequestParam Integer quantity) {

        return ResponseEntity.ok(
                inventoryService.addStock(productId, quantity)
        );
    }

    @PostMapping("/{productId}/reserve")
    public ResponseEntity<String> reserveStock(
            @PathVariable Long productId,
            @RequestParam Integer quantity) {

        InventoryReservationEvent event =
                new InventoryReservationEvent(productId, quantity);

        inventoryEventProducer.publishReservation(event);

        return ResponseEntity.accepted()
                .body("Reservation request published to Kafka");
    }
    @PostMapping("/{productId}/reserve-redis")
    public ResponseEntity<Inventory> reserveStockRedis(
            @PathVariable Long productId,
            @RequestParam Integer quantity) {

        return ResponseEntity.ok(
                inventoryService.reserveStockRedis(productId, quantity)
        );
    }
    @PostMapping("/{productId}/release")
    public ResponseEntity<Inventory> releaseStock(
            @PathVariable Long productId,
            @RequestParam Integer quantity) {

        return ResponseEntity.ok(
                inventoryService.releaseStock(productId, quantity)
        );
    }
    @PatchMapping("/{productId}/reset")
    public Inventory resetStock(
            @PathVariable Long productId,
            @RequestParam Integer quantity) {

        return inventoryService.resetStock(productId, quantity);
    }
}