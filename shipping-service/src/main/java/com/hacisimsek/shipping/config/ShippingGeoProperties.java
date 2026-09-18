package com.hacisimsek.shipping.config;

import com.hacisimsek.shipping.geo.H3Resolution;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Shipping geo/routing configuration from application.yml (shipping.*). */
@ConfigurationProperties(prefix = "shipping")
public record ShippingGeoProperties(
        H3 h3,
        List<Hub> hubs
) {
    public ShippingGeoProperties {
        if (h3 == null) h3 = new H3(8, 6);
        if (hubs == null) hubs = new ArrayList<>();
    }

    public record H3(int resolution, int zoneResolution) {
        public H3 {
            resolution = resolution > 0 ? resolution : H3Resolution.SHIPMENT.getValue();
            zoneResolution = zoneResolution > 0 ? zoneResolution : H3Resolution.ZONE.getValue();
        }
    }

    public record Hub(String name, double lat, double lng) {}
}