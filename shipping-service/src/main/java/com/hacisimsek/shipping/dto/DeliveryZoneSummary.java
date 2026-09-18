package com.hacisimsek.shipping.dto;

import java.util.List;

public record DeliveryZoneSummary(
        String zoneId,
        String hubName,
        long shipmentCount,
        List<String> carriers,
        double avgDistanceKm,
        String representativeCell) {}