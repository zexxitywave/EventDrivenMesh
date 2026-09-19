package com.hacisimsek.inventory.config;

import com.hacisimsek.inventory.model.InventoryItem;
import com.hacisimsek.inventory.model.InventoryStatus;
import com.hacisimsek.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Seeds the Mongo 'inventory' collection with stock for the demo catalog.
 * Product UUIDs must match product-service's data.sql so orders placed with
 * catalog product IDs can pass inventory reservation. Idempotent: existing
 * records are never overwritten.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryDataSeeder implements CommandLineRunner {

    private static final int DEFAULT_QUANTITY = 100;
    private static final int DEFAULT_THRESHOLD = 10;
    private static final String DEFAULT_WAREHOUSE = "WH-A1";

    private final InventoryRepository inventoryRepository;

    private static Map<UUID, String> catalog() {
        Map<UUID, String> products = new LinkedHashMap<>();
        products.put(UUID.fromString("850613db-5e9d-4080-bfa6-4311e8c15e7b"), "Samsung Galaxy S26");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000001"), "Samsung Galaxy S26 Ultra");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000002"), "Samsung Galaxy Z Fold 7");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000003"), "Google Pixel 10 Pro");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000004"), "Xiaomi 15 Ultra");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000005"), "Motorola Edge 50 Ultra");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000006"), "Nothing Phone (3)");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000007"), "Samsung Galaxy A56");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000008"), "Apple MacBook Pro 16");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000009"), "Dell XPS 13");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000010"), "Lenovo ThinkPad X1 Carbon");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000011"), "ASUS ROG Zephyrus G16");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000012"), "Apple iPad Pro 13");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000013"), "Samsung Galaxy Tab S10+");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000014"), "Sony WH-1000XM6");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000015"), "Apple AirPods Pro 3");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000016"), "Bose QuietComfort Ultra");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000017"), "JBL Flip 7");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000018"), "Apple Watch Ultra 3");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000019"), "Galaxy Watch 7");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000020"), "Dyson V15 Detect");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000021"), "Ninja Air Fryer Max");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000022"), "Philips 3200 Espresso");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000023"), "Levi's 501 Original");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000024"), "Nike Air Zoom Pegasus 42");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000025"), "Wilson Evolution");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000026"), "Yonex Astrox 99");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000027"), "Atomic Habits");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000028"), "Clean Code");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000029"), "Organic Rolled Oats 1kg");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000030"), "Extra Virgin Olive Oil 500ml");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000031"), "Vitamin C Serum 30ml");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000032"), "SPF 50 Sunscreen 50ml");
        return products;
    }

    @Override
    public void run(String... args) {
        Map<UUID, String> products = catalog();
        long missing = products.keySet().stream()
                .filter(productId -> inventoryRepository.findByProductId(productId).isEmpty())
                .count();

        if (missing == 0) {
            log.info("Inventory seed: no products missing — nothing to do");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        products.forEach((productId, name) -> {
            if (inventoryRepository.findByProductId(productId).isPresent()) {
                return;
            }
            InventoryItem item = InventoryItem.builder()
                    .id(UUID.randomUUID())
                    .productId(productId)
                    .name(name)
                    .description(name)
                    .availableQuantity(DEFAULT_QUANTITY)
                    .reservedQuantity(0)
                    .warehouseLocation(DEFAULT_WAREHOUSE)
                    .lowStockThreshold(DEFAULT_THRESHOLD)
                    .status(InventoryStatus.IN_STOCK)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            inventoryRepository.save(item);
        });

        log.info("Inventory seed: inserted {} catalog products with {} units each",
                missing, DEFAULT_QUANTITY);
    }
}