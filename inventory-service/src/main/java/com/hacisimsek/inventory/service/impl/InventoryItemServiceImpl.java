package com.hacisimsek.inventory.service.impl;

import com.hacisimsek.inventory.dto.InventoryItemRequest;
import com.hacisimsek.inventory.dto.InventoryItemResponse;
import com.hacisimsek.inventory.dto.RestockRequest;
import com.hacisimsek.inventory.model.InventoryItem;
import com.hacisimsek.inventory.model.InventoryStatus;
import com.hacisimsek.inventory.repository.InventoryRepository;
import com.hacisimsek.inventory.service.InventoryItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryItemServiceImpl implements InventoryItemService {

    private final InventoryRepository inventoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public InventoryItemResponse createInventoryItem(InventoryItemRequest request) {
        if (inventoryRepository.findByProductId(request.getProductId()).isPresent()) {
            throw new RuntimeException("Inventory already exists for product: " + request.getProductId());
        }

        int threshold = request.getLowStockThreshold() == null ? 10 : request.getLowStockThreshold().intValue();
        int qty = request.getQuantity() == null ? 0 : request.getQuantity().intValue();

        InventoryItem item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .productId(request.getProductId())
                .name(request.getName())
                .description(request.getDescription())
                .availableQuantity(qty)
                .reservedQuantity(0)
                .warehouseLocation(request.getWarehouseLocation())
                .lowStockThreshold(threshold)
                .status(resolveStatus(qty, threshold))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        InventoryItem saved = inventoryRepository.save(item);
        log.info("Inventory created for product: {}", request.getProductId());
        return toResponse(saved);
    }

    @Override
    public InventoryItemResponse getInventoryItemById(UUID id) {
        return toResponse(findById(id));
    }

    @Override
    public InventoryItemResponse getInventoryItemByProductId(UUID productId) {
        return toResponse(inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product: " + productId)));
    }

    @Override
    public List<InventoryItemResponse> getAllInventoryItems() {
        return inventoryRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public InventoryItemResponse updateInventoryItem(UUID id, InventoryItemRequest request) {
        InventoryItem item = findById(id);

        if (request.getName() != null)              item.setName(request.getName());
        if (request.getDescription() != null)       item.setDescription(request.getDescription());
        if (request.getWarehouseLocation() != null) item.setWarehouseLocation(request.getWarehouseLocation());
        if (request.getLowStockThreshold() != null) item.setLowStockThreshold(request.getLowStockThreshold());

        if (request.getQuantity() != null) {
            item.setAvailableQuantity(request.getQuantity());
        }

        item.setStatus(resolveStatus(item.getAvailableQuantity(), item.getLowStockThreshold()));
        item.setUpdatedAt(LocalDateTime.now());

        InventoryItem saved = inventoryRepository.save(item);
        checkAndPublishLowStockAlert(saved);
        return toResponse(saved);
    }

    @Override
    public InventoryItemResponse restockItem(UUID id, RestockRequest request) {
        InventoryItem item = findById(id);

        int newQty = item.getAvailableQuantity() + request.getQuantity();
        item.setAvailableQuantity(newQty);
        item.setStatus(resolveStatus(newQty, item.getLowStockThreshold()));
        item.setUpdatedAt(LocalDateTime.now());

        if (request.getWarehouseLocation() != null) {
            item.setWarehouseLocation(request.getWarehouseLocation());
        }

        InventoryItem saved = inventoryRepository.save(item);
        log.info("Restocked product: {} by {} units. New qty: {}", item.getProductId(), request.getQuantity(), newQty);
        return toResponse(saved);
    }

    @Override
    public void deleteInventoryItem(UUID id) {
        InventoryItem item = findById(id);
        inventoryRepository.delete(item);
        log.info("Inventory deleted: {}", id);
    }

    @Override
    public boolean checkAvailability(UUID productId, Integer quantity) {
        return inventoryRepository.findByProductId(productId)
                .map(item -> item.getAvailableQuantity() >= quantity)
                .orElse(false);
    }

    @Override
    public List<InventoryItemResponse> getLowStockItems() {
        return inventoryRepository.findByStatus(InventoryStatus.LOW_STOCK).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private InventoryItem findById(UUID id) {
        return inventoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Inventory item not found: " + id));
    }

    /**
     * Determines the correct status based on current quantity vs threshold.
     */
    private InventoryStatus resolveStatus(int qty, int threshold) {
        if (qty <= 0)         return InventoryStatus.OUT_OF_STOCK;
        if (qty <= threshold) return InventoryStatus.LOW_STOCK;
        return InventoryStatus.IN_STOCK;
    }

    /**
     * Publishes a low-stock alert event to Kafka when an item enters LOW_STOCK status.
     */
    private void checkAndPublishLowStockAlert(InventoryItem item) {
        if (item.getStatus() == InventoryStatus.LOW_STOCK) {
            kafkaTemplate.send("inventory-alerts", Map.of(
                    "type",             "LOW_STOCK_ALERT",
                    "productId",        item.getProductId().toString(),
                    "inventoryId",      item.getId().toString(),
                    "availableQty",     item.getAvailableQuantity(),
                    "threshold",        item.getLowStockThreshold(),
                    "warehouseLocation", item.getWarehouseLocation() != null ? item.getWarehouseLocation() : ""
            ));
            log.warn("LOW STOCK ALERT: product {} — only {} units left (threshold: {})",
                    item.getProductId(), item.getAvailableQuantity(), item.getLowStockThreshold());
        }
    }

    public InventoryItemResponse toResponse(InventoryItem item) {
        return InventoryItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .name(item.getName())
                .description(item.getDescription())
                .availableQuantity(item.getAvailableQuantity())
                .reservedQuantity(item.getReservedQuantity())
                .warehouseLocation(item.getWarehouseLocation())
                .status(item.getStatus())
                .lowStockThreshold(item.getLowStockThreshold())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
