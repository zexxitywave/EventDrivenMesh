package com.locationservice.controller;

import com.locationservice.dto.LocationRequest;
import com.locationservice.dto.LocationResponse;
import com.locationservice.service.LocationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    /**
     * POST /api/location
     * Receives latitude + longitude from the browser, reverse-geocodes them,
     * persists the record, and returns the saved details.
     */
    @PostMapping
    public ResponseEntity<LocationResponse> save(
            @Valid @RequestBody LocationRequest request,
            HttpServletRequest httpRequest
    ) {
        String ip = resolveClientIp(httpRequest);
        LocationResponse response = locationService.saveLocation(request, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/location
     * Returns all saved location records (most-recent first).
     * Useful for an admin view or quick verification.
     */
    @GetMapping
    public ResponseEntity<List<LocationResponse>> getAll() {
        return ResponseEntity.ok(locationService.getAllLocations());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Extracts the real client IP, honouring common reverse-proxy headers.
     * Never trust these headers in a public-facing app without validating
     * that they come from a trusted proxy.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For can contain a comma-separated chain; first entry is the client
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
