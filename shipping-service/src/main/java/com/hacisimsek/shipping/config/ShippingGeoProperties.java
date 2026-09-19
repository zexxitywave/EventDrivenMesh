package com.hacisimsek.shipping.config;

import com.hacisimsek.shipping.geo.H3Resolution;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Shipping geo/routing configuration from application.yml (shipping.*). */
@ConfigurationProperties(prefix = "shipping")
public record ShippingGeoProperties(
        H3 h3,
        List<Hub> hubs,
        Pricing pricing
) {
    public ShippingGeoProperties {
        if (h3 == null) h3 = new H3(8, 6);
        if (hubs == null) hubs = new ArrayList<>();
        if (pricing == null) pricing = new Pricing(BigDecimal.valueOf(49), BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(549), BigDecimal.valueOf(99));
    }

    public record H3(int resolution, int zoneResolution) {
        public H3 {
            resolution = resolution > 0 ? resolution : H3Resolution.SHIPMENT.getValue();
            zoneResolution = zoneResolution > 0 ? zoneResolution : H3Resolution.ZONE.getValue();
        }
    }

    public record Hub(String name, double lat, double lng) {}

    /**
     * Delivery charge formula — charge = baseFee + perKm * distanceToNearestHub,
     * capped at maxCharge. fallbackCharge applies when the address cannot be geocoded.
     */
    public record Pricing(BigDecimal baseFee, BigDecimal perKm,
                          BigDecimal maxCharge, BigDecimal fallbackCharge) {}
}