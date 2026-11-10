package com.flashsale;

import com.flashsale.entity.Category;
import com.flashsale.entity.Inventory;
import com.flashsale.entity.Product;
import com.flashsale.repository.CategoryRepository;
import com.flashsale.repository.InventoryRepository;
import com.flashsale.repository.ProductRepository;
import com.flashsale.service.InventoryService;
import com.flashsale.util.Timeline;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two-buyer demonstration of the flash-sale overselling problem,
 * with a full millisecond-resolution event timeline.
 *
 * Scenario: exactly ONE unit of a product is left in stock, and two
 * buyers (two threads, released simultaneously) each try to reserve
 * that last unit.
 *
 *   1. WITHOUT locking — the naive read-check-update baseline from
 *      docs/concurrency.md. Expected: BOTH buyers confirmed for the
 *      same single unit (overselling / lost update).
 *
 *   2. WITH pessimistic locking — reserveStockPessimistic acquires a
 *      database row lock (SELECT ... FOR UPDATE) before reading and
 *      writing. Expected: exactly ONE buyer wins, the other is
 *      rejected with InsufficientStockException.
 *
 * Each run prints a live timeline of every READ / CHECK / LOCK /
 * SLEEP / WRITE step, followed by an automated analysis that
 * reconstructs the race (or the serialization) from the recorded
 * event timestamps.
 *
 * Infrastructure note: runs on in-memory H2 (PostgreSQL mode) with the
 * Kafka listener disabled, so no Docker containers are required.
 */
@SpringBootTest
@TestPropertySource(properties = {
        // Use H2 in-memory instead of PostgreSQL
        "spring.datasource.url=jdbc:h2:mem:twobuyers;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.kafka.bootstrap-servers=localhost:19095",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class TwoBuyersLockingComparisonTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    // ------------------------------------------------------------
    // Scenario 1 — NO LOCKING
    // ------------------------------------------------------------

    @Test
    void twoBuyersWithoutLocking_oversellsTheLastUnit() throws Exception {

        Long productId =
                createProductWithOneUnit("Flash Sale Phone (no locking)");

        Result[] results = runTwoBuyers(
                "NO LOCKING (naive read-check-update)",
                buyer -> inventoryService.reserveStockNoLocking(productId, 1)
        );

        printReport(productId, results);
        explainLostUpdate();

        // The bug we are demonstrating: both buyers were confirmed
        // even though only one unit existed.
        assertEquals(
                2,
                countConfirmed(results),
                "Naive path should confirm BOTH buyers for the last unit"
        );
    }

    // ------------------------------------------------------------
    // Scenario 2 — PESSIMISTIC LOCKING
    // ------------------------------------------------------------

    @Test
    void twoBuyersWithPessimisticLocking_exactlyOneWins() throws Exception {

        Long productId =
                createProductWithOneUnit("Flash Sale Phone (pessimistic locking)");

        Result[] results = runTwoBuyers(
                "PESSIMISTIC LOCKING (SELECT ... FOR UPDATE)",
                buyer -> inventoryService.reserveStockPessimistic(productId, 1)
        );

        printReport(productId, results);
        explainSerializedAccess(results);

        // Exactly one buyer gets the last unit, the other is rejected.
        assertEquals(
                1,
                countConfirmed(results),
                "Pessimistic locking must confirm exactly ONE buyer"
        );

        Inventory finalInventory =
                inventoryRepository.findByProductId(productId)
                        .orElseThrow();

        assertEquals(0, finalInventory.getAvailableQuantity());
        assertEquals(1, finalInventory.getReservedQuantity());
    }

    // ------------------------------------------------------------
    // Setup / execution helpers
    // ------------------------------------------------------------

    private Long createProductWithOneUnit(String productName) {

        Category category = categoryRepository.save(
                Category.builder()
                        .name("demo-" + System.nanoTime())
                        .description("Two-buyer locking demo")
                        .build()
        );

        Product product = productRepository.save(
                Product.builder()
                        .name(productName)
                        .description("Two-buyer locking demo")
                        .price(new BigDecimal("9.99"))
                        .category(category)
                        .build()
        );

        inventoryRepository.save(
                Inventory.builder()
                        .product(product)
                        .availableQuantity(1)
                        .reservedQuantity(0)
                        .build()
        );

        return product.getId();
    }

    /**
     * Releases both buyers simultaneously and records the live
     * timeline of their reservation attempts. Threads are named
     * "Buyer-1" / "Buyer-2" so timeline events can be attributed.
     */
    private Result[] runTwoBuyers(
            String title,
            ReservationCall call) throws Exception {

        Result[] results = new Result[2];

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch startSignal =
                new CountDownLatch(1);

        CountDownLatch doneSignal =
                new CountDownLatch(2);

        for (int i = 0; i < 2; i++) {

            final int buyerNumber = i + 1;

            executor.submit(() -> {

                Thread.currentThread()
                        .setName("Buyer-" + buyerNumber);

                try {
                    startSignal.await();

                    call.reserve(buyerNumber);

                    results[buyerNumber - 1] =
                            new Result(buyerNumber, true, "OK");

                } catch (Exception e) {

                    results[buyerNumber - 1] =
                            new Result(
                                    buyerNumber,
                                    false,
                                    e.getClass().getSimpleName()
                            );

                } finally {
                    doneSignal.countDown();
                }
            });
        }

        System.out.println();
        System.out.println("=== TIMELINE: " + title + " ===");

        // Reset the clock to t=0 and start recording
        Timeline.enable();

        // Release both buyers at the same moment
        startSignal.countDown();

        doneSignal.await();

        executor.shutdown();

        return results;
    }

    private long countConfirmed(Result[] results) {
        return Arrays.stream(results)
                .filter(Result::success)
                .count();
    }

    // ------------------------------------------------------------
    // Reports and automated analysis
    // ------------------------------------------------------------

    private void printReport(Long productId, Result[] results) {

        Inventory finalInventory =
                inventoryRepository.findByProductId(productId)
                        .orElseThrow();

        long confirmations = countConfirmed(results);

        System.out.println("----------------------------------------------------");

        for (Result result : results) {
            System.out.println(
                    "   Buyer " + result.buyer() + ": "
                            + (result.success()
                            ? "CONFIRMED  (order placed)"
                            : "REJECTED   (" + result.detail() + ")")
            );
        }

        System.out.println("----------------------------------------------------");
        System.out.println(" Confirmations issued : "
                + confirmations + "   (stock was 1)");
        System.out.println(" Final available      : "
                + finalInventory.getAvailableQuantity());
        System.out.println(" Final reserved        : "
                + finalInventory.getReservedQuantity());

        if (confirmations > 1) {
            System.out.println(" >>> OVERSOLD! " + confirmations
                    + " buyers were confirmed for 1 unit of stock.");
        } else {
            System.out.println(" >>> SAFE: exactly 1 buyer"
                    + " got the last unit.");
        }
    }

    /**
     * Reconstructs the lost-update race from the recorded timeline:
     * proves that both buyers READ the last unit before either of
     * them WROTE their claim, i.e. both decided on stale data.
     */
    private void explainLostUpdate() {

        List<Timeline.Event> events = Timeline.events();

        Timeline.Event read1 = findEvent(events, "Buyer-1", "READ");
        Timeline.Event read2 = findEvent(events, "Buyer-2", "READ");
        Timeline.Event write1 = findEvent(events, "Buyer-1", "WRITE");
        Timeline.Event write2 = findEvent(events, "Buyer-2", "WRITE");

        if (read1 == null || read2 == null
                || write1 == null || write2 == null) {
            return;
        }

        long lastRead = Math.max(read1.ms(), read2.ms());
        long firstWrite = Math.min(write1.ms(), write2.ms());

        System.out.println();
        System.out.println(">>> WHY IT OVERSOLD (lost update):");
        System.out.println("    Buyer 1: " + read1.message()
                + "  (t=" + read1.ms() + " ms)");
        System.out.println("    Buyer 2: " + read2.message()
                + "  (t=" + read2.ms() + " ms)   <- SAME stale value");
        System.out.println("    Buyer 1: " + write1.message()
                + "  (t=" + write1.ms() + " ms)");
        System.out.println("    Buyer 2: " + write2.message()
                + "  (t=" + write2.ms() + " ms)");
        System.out.println("    -> The last read (t=" + lastRead
                + " ms) finished BEFORE the first write (t="
                + firstWrite + " ms).");
        System.out.println("       Both buyers made their decision on"
                + " the SAME stale data,");
        System.out.println("       so both were confirmed for 1 unit"
                + " of stock.");

        // Programmatically prove the stale read
        assertTrue(
                lastRead < firstWrite,
                "Both reads must happen before the first write"
        );
    }

    /**
     * Reconstructs the serialized execution from the recorded
     * timeline: shows the losing buyer BLOCKING on the row lock,
     * only reading the row after the winner committed, and failing
     * the stock check.
     */
    private void explainSerializedAccess(Result[] results) {

        Result winner = Arrays.stream(results)
                .filter(Result::success)
                .findFirst()
                .orElseThrow();

        Result loser = Arrays.stream(results)
                .filter(r -> !r.success())
                .findFirst()
                .orElseThrow();

        List<Timeline.Event> events = Timeline.events();

        String winnerThread = "Buyer-" + winner.buyer();
        String loserThread = "Buyer-" + loser.buyer();

        Timeline.Event wReq =
                findEvent(events, winnerThread, "LOCK    request");
        Timeline.Event wAcq =
                findEvent(events, winnerThread, "LOCK    acquired");
        Timeline.Event lReq =
                findEvent(events, loserThread, "LOCK    request");
        Timeline.Event lAcq =
                findEvent(events, loserThread, "LOCK    acquired");

        System.out.println();
        System.out.println(">>> WHY IT IS SAFE (row lock serializes buyers):");

        if (wAcq != null && wReq != null) {
            System.out.println("    Buyer " + winner.buyer()
                    + " (winner): acquired the row lock at t="
                    + wAcq.ms() + " ms (waited "
                    + (wAcq.ms() - wReq.ms()) + " ms)");
        }

        if (lAcq != null && lReq != null) {
            long blockedFor = lAcq.ms() - lReq.ms();

            System.out.println("    Buyer " + loser.buyer()
                    + " (loser) : requested the lock at t="
                    + lReq.ms() + " ms and was BLOCKED for "
                    + blockedFor + " ms until Buyer "
                    + winner.buyer() + "'s transaction committed");
            System.out.println("    Buyer " + loser.buyer()
                    + " only READ the row AFTER that commit:"
                    + " available=0 -> CHECK FAIL -> REJECTED");

            // Programmatically prove the blocking
            assertTrue(
                    blockedFor >= 150,
                    "The losing buyer must have been blocked"
                            + " on the row lock"
            );
        }
    }

    private Timeline.Event findEvent(
            List<Timeline.Event> events,
            String thread,
            String messagePrefix) {

        return events.stream()
                .filter(event -> event.thread().equals(thread))
                .filter(event -> event.message().startsWith(messagePrefix))
                .findFirst()
                .orElse(null);
    }

    @FunctionalInterface
    private interface ReservationCall {
        void reserve(int buyerNumber) throws Exception;
    }

    private record Result(
            int buyer,
            boolean success,
            String detail) {
    }
}
