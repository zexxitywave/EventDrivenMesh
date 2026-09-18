package com.hacisimsek.shipping.geo;

import java.util.List;

/** Bare-metal distance math used for the zone summary. */
public final class GeoMath {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoMath() {}

    /** Great-circle distance in kilometres between two coordinates. */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Delivery-time estimate from hex hops: ~40 km/h road-equivalent, bounded. */
    public static double etaHoursFromKm(double km) {
        double hours = km / 40.0;
        return Math.max(4, Math.min(72, hours));
    }
}