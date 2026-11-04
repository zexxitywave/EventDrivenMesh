package com.locationservice.service;

import com.locationservice.dto.GeocodingResult;
import com.locationservice.dto.LocationRequest;
import com.locationservice.dto.LocationResponse;
import com.locationservice.entity.UserLocation;
import com.locationservice.repository.UserLocationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final UserLocationRepository repository;
    private final GeocodingService       geocodingService;

    /**
     * Saves a location capture.
     * <ol>
     *   <li>Performs reverse geocoding to obtain a human-readable address.</li>
     *   <li>Persists the full record to PostgreSQL.</li>
     *   <li>Returns a response DTO.</li>
     * </ol>
     *
     * @param request   validated coordinate payload from the browser
     * @param ipAddress caller IP (from HttpServletRequest, for audit)
     */
    public LocationResponse saveLocation(LocationRequest request, String ipAddress) {
        log.info("Saving location: lat={}, lon={}", request.latitude(), request.longitude());

        // 1. Reverse geocode
        GeocodingResult geo = geocodingService.reverseGeocode(request.latitude(), request.longitude());

        // 2. Build and persist the entity
        UserLocation entity = UserLocation.builder()
                .latitude(request.latitude())
                .longitude(request.longitude())
                .accuracy(request.accuracy())
                .address(geo.fullAddress())
                .city(geo.city())
                .country(geo.country())
                .ipAddress(ipAddress)
                .build();

        UserLocation saved = repository.save(entity);
        log.info("Location saved with id={}", saved.getId());

        return toResponse(saved);
    }

    /** Returns all saved locations, most recent first. */
    public List<LocationResponse> getAllLocations() {
        return repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private LocationResponse toResponse(UserLocation e) {
        return new LocationResponse(
                e.getId(),
                e.getLatitude(),
                e.getLongitude(),
                e.getAccuracy(),
                e.getAddress(),
                e.getCity(),
                e.getCountry(),
                e.getCreatedAt()
        );
    }
}
