package com.flashsale.repository;

import com.flashsale.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository
        extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);

    /**
     * Bulk quantity write-back. Intentionally bypasses the optimistic
     * lock (@Version) check — used only by the unprotected baseline
     * reservation strategy (reserveStockNoLocking) to reproduce the
     * read-check-update race documented in docs/concurrency.md.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Inventory i SET i.availableQuantity = :available, i.reservedQuantity = :reserved WHERE i.product.id = :productId")
    void updateQuantitiesUnchecked(
            @Param("productId") Long productId,
            @Param("available") Integer available,
            @Param("reserved") Integer reserved);
}
