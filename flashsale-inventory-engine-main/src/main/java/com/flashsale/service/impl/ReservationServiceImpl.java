package com.flashsale.service.impl;

import com.flashsale.entity.Inventory;
import com.flashsale.entity.InventoryReservation;
import com.flashsale.entity.ReservationStatus;
import com.flashsale.entity.User;
import com.flashsale.repository.InventoryRepository;
import com.flashsale.repository.InventoryReservationRepository;
import com.flashsale.repository.UserRepository;
import com.flashsale.service.ReservationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationServiceImpl implements ReservationService {

    private final InventoryReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;

    public ReservationServiceImpl(
            InventoryReservationRepository reservationRepository,
            InventoryRepository inventoryRepository,
            UserRepository userRepository) {
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public InventoryReservation createReservation(Long productId, Long userId, Integer quantity, int ttlMinutes) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product: " + productId));

        if (inventory.getAvailableQuantity() < quantity) {
            throw new RuntimeException("Insufficient stock for reservation");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
        inventoryRepository.save(inventory);

        InventoryReservation reservation = InventoryReservation.builder()
                .inventory(inventory)
                .user(user)
                .quantity(quantity)
                .status(ReservationStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusMinutes(ttlMinutes))
                .createdAt(LocalDateTime.now())
                .build();

        return reservationRepository.save(reservation);
    }

    @Override
    @Transactional
    public void confirmReservation(Long reservationId) {
        InventoryReservation reservation = getReservationById(reservationId);
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new RuntimeException("Only ACTIVE reservations can be confirmed");
        }
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation);
    }

    @Override
    @Transactional
    public void cancelReservation(Long reservationId) {
        InventoryReservation reservation = getReservationById(reservationId);
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return;
        }

        Inventory inventory = reservation.getInventory();
        inventory.setReservedQuantity(inventory.getReservedQuantity() - reservation.getQuantity());
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + reservation.getQuantity());
        inventoryRepository.save(inventory);

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
    }

    @Override
    @Transactional
    public int expireStaleReservations() {
        List<InventoryReservation> expired = reservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.ACTIVE, LocalDateTime.now());

        for (InventoryReservation reservation : expired) {
            cancelReservation(reservation.getId());
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);
        }

        return expired.size();
    }

    @Override
    public InventoryReservation getReservationById(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + reservationId));
    }
}
