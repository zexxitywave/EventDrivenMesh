package com.hacisimsek.shipping.service;

import com.hacisimsek.common.dto.ShippingQuoteResponse;
import com.hacisimsek.shipping.config.ShippingGeoProperties;
import com.hacisimsek.shipping.geo.DeliveryZoneService;
import com.hacisimsek.shipping.geo.GeocodingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

/**
 * Pre-payment delivery quote. Geocodes the order address (or falls back when
 * geocoding is unavailable) and prices the delivery using the configured
 * baseFee + perKm * distance formula, capped at maxCharge.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingQuoteService {

    private static final String[] CARRIERS = {"DHL", "FedEx", "UPS", "USPS"};

    private final GeocodingClient geocodingClient;
    private final DeliveryZoneService deliveryZoneService;
    private final ShippingGeoProperties properties;

    public ShippingQuoteResponse quote(String address) {
        return geocodingClient.geocode(address)
                .map(point -> {
                    DeliveryZoneService.ZoneAssignment a = deliveryZoneService.assign(point);
                    return new ShippingQuoteResponse(
                            address,
                            computeCharge(a.distanceKm()),
                            a.estimatedDeliveryDate(),
                            a.carrier(),
                            a.zoneId(),
                            a.hubName(),
                            a.distanceKm(),
                            a.h3Cell(),
                            point.lat(),
                            point.lng(),
                            true);
                })
                .orElseGet(() -> {
                    log.warn("[Quote] no geocode for '{}' — applying fallback charge", address);
                    return new ShippingQuoteResponse(
                            address,
                            prices().fallbackCharge(),
                            Instant.now().plus(3, ChronoUnit.DAYS),
                            randomCarrier(),
                            null, null, null, null, null, null,
                            false);
                });
    }

    private BigDecimal computeCharge(double distanceKm) {
        ShippingGeoProperties.Pricing p = prices();
        BigDecimal raw = p.baseFee()
                .add(p.perKm().multiply(BigDecimal.valueOf(distanceKm)));
        if (raw.compareTo(p.maxCharge()) > 0) {
            return p.maxCharge().setScale(2, RoundingMode.HALF_UP);
        }
        return raw.setScale(2, RoundingMode.HALF_UP);
    }

    private ShippingGeoProperties.Pricing prices() {
        return properties.pricing() != null
                ? properties.pricing()
                : new ShippingGeoProperties.Pricing(
                        BigDecimal.valueOf(49), BigDecimal.valueOf(1.5),
                        BigDecimal.valueOf(549), BigDecimal.valueOf(99));
    }

    private String randomCarrier() {
        return CARRIERS[new Random().nextInt(CARRIERS.length)];
    }
}