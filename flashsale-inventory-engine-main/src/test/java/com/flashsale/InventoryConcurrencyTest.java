package com.flashsale;

import com.flashsale.entity.Inventory;
import com.flashsale.service.RedisInventoryService;
import com.flashsale.repository.InventoryRepository;
import com.flashsale.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.flashsale.service.InventoryEventProducer;
import com.flashsale.event.InventoryReservationEvent;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
class InventoryConcurrencyTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private RedisInventoryService redisInventoryService;

    @Autowired
    private InventoryEventProducer inventoryEventProducer;

    @Test
    void testConcurrentReservation() throws Exception {

        Long productId = 1L;

        // Reset inventory to 10
        Inventory inventory = inventoryService.getInventory(productId);
        inventory.setAvailableQuantity(10);
        inventory.setReservedQuantity(0);
        inventoryRepository.save(inventory);

        int numberOfRequests = 50;

        ExecutorService executor =
                Executors.newFixedThreadPool(20);

        CountDownLatch startSignal =
                new CountDownLatch(1);

        CountDownLatch doneSignal =
                new CountDownLatch(numberOfRequests);

        AtomicInteger successfulReservations =
                new AtomicInteger(0);

        AtomicInteger failedReservations =
                new AtomicInteger(0);

        for (int i = 0; i < numberOfRequests; i++) {

            executor.submit(() -> {

                try {
                    startSignal.await();

                    inventoryService.reserveStock(productId, 1);

                    successfulReservations.incrementAndGet();

                } catch (Exception e) {

                    failedReservations.incrementAndGet();

                    System.out.println(
                            "Reservation failed: "
                                    + e.getClass().getSimpleName()
                    );


                } finally  {
                    doneSignal.countDown();
                }
            });
        }

        // Start all threads together
        startSignal.countDown();

        // Wait for all requests
        doneSignal.await();

        executor.shutdown();

        Inventory finalInventory =
                inventoryService.getInventory(productId);

        System.out.println("================================");
        System.out.println("Concurrent requests: " + numberOfRequests);
        System.out.println(
                "Successful reservations: "
                        + successfulReservations.get()
        );
        System.out.println(
                "Available stock: "
                        + finalInventory.getAvailableQuantity()
        );
        System.out.println(
                "Reserved stock: "
                        + finalInventory.getReservedQuantity()
        );

        System.out.println(
                "Invariant: " +
                        (finalInventory.getAvailableQuantity()
                                + finalInventory.getReservedQuantity())
        );
        System.out.println(
                "Failed reservations: "
                        + failedReservations.get()
        );
        System.out.println("================================");
    }
    @Test
    void testConcurrentReservationPessimistic() throws Exception {

        Long productId = 1L;

        // Reset inventory
        Inventory inventory = inventoryService.getInventory(productId);
        inventory.setAvailableQuantity(10);
        inventory.setReservedQuantity(0);
        inventoryRepository.save(inventory);

        int numberOfRequests = 50;

        ExecutorService executor =
                Executors.newFixedThreadPool(20);

        CountDownLatch startSignal =
                new CountDownLatch(1);

        CountDownLatch doneSignal =
                new CountDownLatch(numberOfRequests);

        AtomicInteger successfulReservations =
                new AtomicInteger(0);

        AtomicInteger failedReservations =
                new AtomicInteger(0);

        for (int i = 0; i < numberOfRequests; i++) {

            executor.submit(() -> {

                try {
                    startSignal.await();

                    inventoryService.reserveStockPessimistic(
                            productId, 1
                    );

                    successfulReservations.incrementAndGet();

                } catch (Exception e) {

                    failedReservations.incrementAndGet();

                } finally {
                    doneSignal.countDown();
                }
            });
        }

        // Start all requests
        startSignal.countDown();

        // Wait for completion
        doneSignal.await();

        executor.shutdown();

        Inventory finalInventory =
                inventoryService.getInventory(productId);

        System.out.println("================================");
        System.out.println("PESSIMISTIC LOCKING");
        System.out.println("Concurrent requests: " + numberOfRequests);
        System.out.println(
                "Successful reservations: "
                        + successfulReservations.get()
        );
        System.out.println(
                "Failed reservations: "
                        + failedReservations.get()
        );
        System.out.println(
                "Available stock: "
                        + finalInventory.getAvailableQuantity()
        );
        System.out.println(
                "Reserved stock: "
                        + finalInventory.getReservedQuantity()
        );
        System.out.println(
                "Invariant: "
                        + (finalInventory.getAvailableQuantity()
                        + finalInventory.getReservedQuantity())
        );
        System.out.println("================================");
    }
    @Test
    void testConcurrentRedisReservation() throws Exception {

        Long productId = 1L;

        // Initialize Redis stock
        redisInventoryService.initializeStock(productId, 10);

        int numberOfRequests = 50;

        ExecutorService executor =
                Executors.newFixedThreadPool(20);

        CountDownLatch startSignal =
                new CountDownLatch(1);

        CountDownLatch doneSignal =
                new CountDownLatch(numberOfRequests);

        AtomicInteger successfulReservations =
                new AtomicInteger(0);

        AtomicInteger failedReservations =
                new AtomicInteger(0);

        for (int i = 0; i < numberOfRequests; i++) {

            executor.submit(() -> {

                try {
                    startSignal.await();

                    boolean success =
                            redisInventoryService.reserveStock(productId, 1);

                    if (success) {
                        successfulReservations.incrementAndGet();
                    } else {
                        failedReservations.incrementAndGet();
                    }

                } catch (Exception e) {

                    failedReservations.incrementAndGet();

                } finally {
                    doneSignal.countDown();
                }
            });
        }

        startSignal.countDown();

        doneSignal.await();

        executor.shutdown();

        Long remainingStock =
                redisInventoryService.getStock(productId);

        // Guard against null (key absent from Redis)
        long safeRemainingStock = remainingStock != null ? remainingStock : 0L;

        System.out.println("================================");
        System.out.println("REDIS INVENTORY");
        System.out.println("Concurrent requests: " + numberOfRequests);
        System.out.println(
                "Successful reservations: "
                        + successfulReservations.get()
        );
        System.out.println(
                "Failed reservations: "
                        + failedReservations.get()
        );
        System.out.println(
                "Remaining stock: "
                        + safeRemainingStock
        );
        System.out.println(
                "Invariant: "
                        + (successfulReservations.get()
                        + safeRemainingStock)
        );
        System.out.println("================================");

        assertEquals(10, successfulReservations.get() + safeRemainingStock);
    }
    @Test
    void testKafkaProducer() {

        InventoryReservationEvent event =
                new InventoryReservationEvent(1L, 1);

        inventoryEventProducer.publishReservation(event);

        System.out.println("Kafka event published");
    }
    @Test
    void testDuplicateKafkaEvent() {

        InventoryReservationEvent event =
                new InventoryReservationEvent(
                        "test-duplicate-001",
                        1L,
                        1
                );

        // Publish SAME event twice
        inventoryEventProducer.publishReservation(event);
        inventoryEventProducer.publishReservation(event);

        System.out.println("Duplicate Kafka events published");
    }
    @Test
    void testConcurrentDuplicateKafkaEvents() throws Exception {

        Long productId = 1L;

        // Reset inventory
        Inventory inventory = inventoryService.getInventory(productId);
        inventory.setAvailableQuantity(10);
        inventory.setReservedQuantity(0);
        inventoryRepository.save(inventory);

        String eventId = "concurrent-duplicate-001";

        InventoryReservationEvent event =
                new InventoryReservationEvent(
                        eventId,
                        productId,
                        1
                );

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch startSignal =
                new CountDownLatch(1);

        for (int i = 0; i < 2; i++) {

            executor.submit(() -> {

                try {
                    startSignal.await();

                    inventoryEventProducer.publishReservation(event);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        startSignal.countDown();

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("Concurrent duplicate events published");
    }
}