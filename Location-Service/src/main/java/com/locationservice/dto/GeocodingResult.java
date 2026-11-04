package com.locationservice.dto;

/**
 * Internal value object returned by the geocoding client.
 */
public record GeocodingResult(
        String fullAddress,
        String city,
        String country
) {
    /** Convenience factory for the "nothing found" case. */
    public static GeocodingResult empty() {
        return new GeocodingResult(null, null, null);
    }
}
