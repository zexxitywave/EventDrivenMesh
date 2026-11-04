package com.locationservice.dto;

import java.time.LocalDateTime;

/**
 * JSON sent back to the browser after a successful save.
 */
public record LocationResponse(
        Long   id,
        Double latitude,
        Double longitude,
        Double accuracy,
        String address,
        String city,
        String country,
        LocalDateTime createdAt
) {}
