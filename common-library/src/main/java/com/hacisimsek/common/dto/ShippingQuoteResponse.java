package com.hacisimsek.common.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ShippingQuoteResponse(
        String address,
        BigDecimal deliveryCharge,
        Instant estimatedDeliveryDate,
        String carrier,
        String zoneId,
        String hubName,
        Double distanceKm,
        String h3Cell,
        Double latitude,
        Double longitude,
        boolean geocoded) {}