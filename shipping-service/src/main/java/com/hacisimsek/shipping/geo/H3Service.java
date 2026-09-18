package com.hacisimsek.shipping.geo;

import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class H3Service {

    private final H3Core h3;

    public H3Service() {
        try {
            this.h3 = H3Core.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise H3", e);
        }
    }

    /** Snap a coordinate to its hex cell at the given resolution. */
    public String encode(double lat, double lng, int resolution) {
        return h3ToString(h3.latLngToCell(lat, lng, resolution));
    }

    /** Cells within k rings around the given cell. */
    public List<String> neighbors(String cell, int k) {
        return h3.gridDisk(stringToH3(cell), k).stream()
                .map(this::h3ToString)
                .collect(Collectors.toList());
    }

    /** Hex-grid hops between two cells (-1 when unreachable within the max ring). */
    public long gridDistance(String from, String to) {
        return h3.gridDistance(stringToH3(from), stringToH3(to));
    }

    /** Cell centre as {lat, lng}. */
    public double[] centroid(String cell) {
        LatLng ll = h3.cellToLatLng(stringToH3(cell));
        return new double[]{ll.lat, ll.lng};
    }

    /** Coarser parent cell — used to define a delivery zone (stable, spatially contiguous). */
    public String parent(String cell, int parentResolution) {
        return h3ToString(h3.cellToParent(stringToH3(cell), parentResolution));
    }

    private String h3ToString(long h3Index) {
        return h3.h3ToString(h3Index);
    }

    private long stringToH3(String cell) {
        return h3.stringToH3(cell);
    }
}