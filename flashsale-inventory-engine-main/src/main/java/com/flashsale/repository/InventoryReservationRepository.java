package com.flashsale.repository;

import com.flashsale.entity.InventoryReservation;
import com.flashsale.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    List<InventoryReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime now);
}
