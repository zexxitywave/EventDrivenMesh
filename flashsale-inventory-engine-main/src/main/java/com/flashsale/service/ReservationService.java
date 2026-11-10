package com.flashsale.service;

import com.flashsale.entity.InventoryReservation;

public interface ReservationService {

    InventoryReservation createReservation(Long productId, Long userId, Integer quantity, int ttlMinutes);

    void confirmReservation(Long reservationId);

    void cancelReservation(Long reservationId);

    int expireStaleReservations();

    InventoryReservation getReservationById(Long reservationId);
}
