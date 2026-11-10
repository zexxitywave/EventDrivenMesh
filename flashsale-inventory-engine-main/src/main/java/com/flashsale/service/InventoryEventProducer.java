package com.flashsale.service;

import com.flashsale.event.InventoryReservationEvent;

public interface InventoryEventProducer {

    void publishReservation(InventoryReservationEvent event);
}