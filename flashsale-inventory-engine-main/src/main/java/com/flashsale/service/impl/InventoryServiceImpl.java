package com.flashsale.service.impl;

import com.flashsale.entity.Inventory;
import com.flashsale.entity.Product;
import com.flashsale.repository.InventoryRepository;
import com.flashsale.repository.ProductRepository;
import com.flashsale.service.InventoryService;
import com.flashsale.exception.InsufficientStockException;
import com.flashsale.service.RedisInventoryService;
import com.flashsale.service.InventoryEventProducer;
import com.flashsale.event.InventoryReservationEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flashsale.service.MetricsService;
import com.flashsale.util.Timeline;

@Service
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    private final RedisInventoryService redisInventoryService;
    private final InventoryEventProducer inventoryEventProducer;

    private final MetricsService metricsService;

    public InventoryServiceImpl(
            InventoryRepository inventoryRepository,
            ProductRepository productRepository,
            RedisInventoryService redisInventoryService,
            InventoryEventProducer inventoryEventProducer,
            MetricsService metricsService) {

        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.redisInventoryService = redisInventoryService;
        this.inventoryEventProducer = inventoryEventProducer;
        this.metricsService = metricsService;
    }

    @Override
    public Inventory createInventory(Long productId, Integer quantity) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Inventory inventory = Inventory.builder()
                .product(product)
                .availableQuantity(quantity)
                .reservedQuantity(0)
                .build();

        return inventoryRepository.save(inventory);
    }

    @Override
    public Inventory getInventory(Long productId) {

        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found"));
    }

    @Override
    @Transactional
    public Inventory addStock(Long productId, Integer quantity) {

        Inventory inventory = getInventory(productId);

        inventory.setAvailableQuantity(
                inventory.getAvailableQuantity() + quantity
        );

        return inventoryRepository.save(inventory);
    }

    @Override
    @Transactional
    public Inventory reserveStock(Long productId, Integer quantity) {

        Inventory inventory = getInventory(productId);

        if (inventory.getAvailableQuantity() < quantity) {
            metricsService.reservationFailure();
            throw new InsufficientStockException("Insufficient stock");
        }

        inventory.setAvailableQuantity(
                inventory.getAvailableQuantity() - quantity
        );

        inventory.setReservedQuantity(
                inventory.getReservedQuantity() + quantity
        );

        Inventory savedInventory =
                inventoryRepository.save(inventory);

        metricsService.reservationSuccess();

        return savedInventory;
    }

    @Transactional
    @Override
    public Inventory reserveStockPessimistic(
            Long productId,
            Integer quantity) {

        long lockRequestNanos = System.nanoTime();

        Timeline.event("LOCK    request: SELECT ... FOR UPDATE");

        Inventory inventory = inventoryRepository
                .findByProductIdForUpdate(productId)
                .orElseThrow(() ->
                        new RuntimeException("Inventory not found"));

        long lockWaitMs =
                (System.nanoTime() - lockRequestNanos) / 1_000_000;

        Timeline.event(String.format(
                "LOCK    acquired (waited %d ms) -> READ row: available=%d, reserved=%d",
                lockWaitMs,
                inventory.getAvailableQuantity(),
                inventory.getReservedQuantity()
        ));

        if (inventory.getAvailableQuantity() < quantity) {
            Timeline.event(String.format(
                    "CHECK   available(%d) < requested(%d) -> FAIL",
                    inventory.getAvailableQuantity(),
                    quantity
            ));
            throw new InsufficientStockException("Insufficient stock");
        }

        Timeline.event(String.format(
                "CHECK   available(%d) >= requested(%d) -> PASS",
                inventory.getAvailableQuantity(),
                quantity
        ));

        inventory.setAvailableQuantity(
                inventory.getAvailableQuantity() - quantity
        );

        inventory.setReservedQuantity(
                inventory.getReservedQuantity() + quantity
        );

        try {
            // Artificial delay (demo/experiment methodology from
            // docs/concurrency.md) while HOLDING the row lock — makes
            // the second buyer's blocking visible in the timeline.
            Timeline.event("SLEEP   300 ms simulated processing (row lock held)");
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Reservation interrupted", e);
        }

        Timeline.event(String.format(
                "WRITE   row <- available=%d, reserved=%d (lock held until commit)",
                inventory.getAvailableQuantity(),
                inventory.getReservedQuantity()
        ));

        return inventoryRepository.save(inventory);
    }

    /**
     * Baseline reservation strategy — intentionally UNPROTECTED.
     *
     * Mirrors the original read-check-update flow from
     * docs/concurrency.md ("Baseline Implementation"). The stock check
     * is performed against the row read at the start of the transaction,
     * and the computed quantities are written back with a bulk JPQL
     * update that deliberately bypasses the optimistic-lock (@Version)
     * check — i.e. neither a row lock nor a version guard protects the
     * operation.
     *
     * An artificial delay between the read and the write widens the
     * race window so the two-buyer demonstration is deterministic
     * (same methodology as the experiments in docs/concurrency.md).
     *
     * Expected outcome: two concurrent buyers can BOTH receive a
     * confirmation for the same last unit of stock (overselling).
     */
    @Transactional
    @Override
    public Inventory reserveStockNoLocking(
            Long productId,
            Integer quantity) {

        Inventory inventory = getInventory(productId);

        Timeline.event(String.format(
                "READ    row -> available=%d, reserved=%d",
                inventory.getAvailableQuantity(),
                inventory.getReservedQuantity()
        ));

        if (inventory.getAvailableQuantity() < quantity) {
            Timeline.event(String.format(
                    "CHECK   available(%d) < requested(%d) -> FAIL",
                    inventory.getAvailableQuantity(),
                    quantity
            ));
            throw new InsufficientStockException("Insufficient stock");
        }

        Timeline.event(String.format(
                "CHECK   available(%d) >= requested(%d) -> PASS",
                inventory.getAvailableQuantity(),
                quantity
        ));

        int newAvailable =
                inventory.getAvailableQuantity() - quantity;

        int newReserved =
                inventory.getReservedQuantity() + quantity;

        try {
            // Artificial delay (demo/experiment only) — keeps the
            // read-check and the write-back windows wide enough to
            // reliably expose the race (docs/concurrency.md
            // methodology).
            Timeline.event("SLEEP   500 ms simulated processing (race window open)");
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Reservation interrupted", e);
        }

        Timeline.event(String.format(
                "WRITE   row <- available=%d, reserved=%d (no lock, no @Version guard)",
                newAvailable,
                newReserved
        ));

        inventoryRepository.updateQuantitiesUnchecked(
                productId, newAvailable, newReserved
        );

        return getInventory(productId);
    }

    @Override
    public Inventory reserveStockRedis(
            Long productId,
            Integer quantity) {

        boolean reserved = redisInventoryService
                .reserveStock(productId, quantity);

        if (!reserved) {
            throw new InsufficientStockException(
                    "Insufficient stock in Redis"
            );
        }

        inventoryEventProducer.publishReservation(
                new InventoryReservationEvent(productId, quantity)
        );

        return getInventory(productId);
    }

    @Override
    @Transactional
    public Inventory releaseStock(
            Long productId,
            Integer quantity) {

        Inventory inventory = getInventory(productId);

        if (inventory.getReservedQuantity() < quantity) {
            throw new RuntimeException(
                    "Cannot release more stock than reserved"
            );
        }

        inventory.setReservedQuantity(
                inventory.getReservedQuantity() - quantity
        );

        inventory.setAvailableQuantity(
                inventory.getAvailableQuantity() + quantity
        );

        Inventory savedInventory = inventoryRepository.save(inventory);

        redisInventoryService.releaseStock(productId, quantity);

        return savedInventory;
    }
    @Override
    @Transactional
    public Inventory resetStock(Long productId, Integer quantity) {

        Inventory inventory = getInventory(productId);

        inventory.setAvailableQuantity(quantity);
        inventory.setReservedQuantity(0);

        return inventoryRepository.save(inventory);
    }
}

