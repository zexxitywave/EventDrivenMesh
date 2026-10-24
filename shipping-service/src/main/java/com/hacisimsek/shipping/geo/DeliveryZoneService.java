package com.hacisimsek.shipping.geo;

import com.hacisimsek.shipping.config.ShippingGeoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Turns a geocoded delivery point into: an H3 cell, a delivery zone (coarser
 * parent cell), the nearest configured hub, a zone-consistent carrier and an
 * ETA derived from real distance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryZoneService {

    private static final String[] CARRIERS = {"DHL", "FedEx", "UPS", "USPS"};

    private final H3Service h3Service;
    private final ShippingGeoProperties properties;

    public record ZoneAssignment(
            String h3Cell,
            String zoneId,
            String hubName,
            String carrier,
            double distanceKm,
            Instant estimatedDeliveryDate) {}

    public ZoneAssignment assign(GeocodingClient.GeoPoint point) {
        int res = properties.h3().resolution();
        int zoneRes = properties.h3().zoneResolution();

        String cell = h3Service.encode(point.lat(), point.lng(), res);
        String zoneId = h3Service.parent(cell, zoneRes);

        ShippingGeoProperties.Hub nearest = nearestHub(point.lat(), point.lng());
        double distanceKm = nearest != null
                ? GeoMath.haversineKm(point.lat(), point.lng(), nearest.lat(), nearest.lng())
                : 0.0;

        String carrier = CARRIERS[Math.floorMod(zoneId.hashCode(), CARRIERS.length)];
        double etaHours = distanceKm > 0 ? GeoMath.etaHoursFromKm(distanceKm) : 48.0;

        Instant eta = Instant.now().plus(Duration.ofMinutes(Math.round(etaHours * 60)));

        return new ZoneAssignment(cell, zoneId,
                nearest != null ? nearest.name() : "Unassigned",
                carrier, distanceKm, eta);
    }

    private ShippingGeoProperties.Hub nearestHub(double lat, double lng) {
        List<ShippingGeoProperties.Hub> hubs = properties.hubs();
        if (hubs == null || hubs.isEmpty()) {
            return null;
        }
        ShippingGeoProperties.Hub best = null;
        double bestKm = Double.MAX_VALUE;
        for (ShippingGeoProperties.Hub hub : hubs) {
            double km = GeoMath.haversineKm(lat, lng, hub.lat(), hub.lng());
            if (km < bestKm) {
                bestKm = km;
                best = hub;
            }
        }
        return best;
    }
}