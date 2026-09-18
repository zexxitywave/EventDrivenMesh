package com.hacisimsek.shipping.geo;

import lombok.Getter;

/** Fixed Uber H3 resolutions used by shipping. */
@Getter
public enum H3Resolution {

    /** Cell size for geocoding each shipment (~3 km hexagons). */
    SHIPMENT(8),
    /** Coarser parent cell that defines a delivery zone (~36 km hexagons). */
    ZONE(6);

    private final int value;

    H3Resolution(int value) {
        this.value = value;
    }
}