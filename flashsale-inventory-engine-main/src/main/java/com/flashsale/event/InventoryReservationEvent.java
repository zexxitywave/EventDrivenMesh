package com.flashsale.event;

import java.util.UUID;

public record InventoryReservationEvent(
        String eventId,
        Long productId,
        int quantity
) {
    public InventoryReservationEvent(Long productId , int quantity){
        this(UUID.randomUUID().toString(), productId, quantity);
    }

}
