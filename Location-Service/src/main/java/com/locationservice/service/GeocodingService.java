package com.locationservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locationservice.dto.GeocodingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Reverse-geocoding using Geoapify API (free tier: 3000 req/day).
 * Falls back to Nominatim if no Geoapify key is configured.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

    @Value("${geocoding.nominatim-url}")
    private String nominatimUrl;

    @Value("${geocoding.user-agent}")
    private String userAgent;

    @Value("${geocoding.geoapify-api-key:}")
    private String geoapifyApiKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeocodingResult reverseGeocode(double latitude, double longitude) {
        if (geoapifyApiKey != null && !geoapifyApiKey.isBlank()) {
            return reverseGeocodeGeoapify(latitude, longitude);
        }
        return reverseGeocodeNominatim(latitude, longitude);
    }

    // ── Geoapify Reverse Geocoding ────────────────────────────────────────────

    private GeocodingResult reverseGeocodeGeoapify(double lat, double lon) {
        String url = String.format(
                "https://api.geoapify.com/v1/geocode/reverse?lat=%.7f&lon=%.7f&format=json&apiKey=%s",
                lat, lon, geoapifyApiKey
        );
        log.info("Calling Geoapify for ({}, {})", lat, lon);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Geoapify HTTP {}", response.statusCode());

            if (response.statusCode() != 200) {
                log.warn("Geoapify returned HTTP {}: {}", response.statusCode(), response.body());
                return GeocodingResult.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.path("results");

            if (!results.isArray() || results.isEmpty()) {
                log.warn("Geoapify returned no results");
                return GeocodingResult.empty();
            }

            JsonNode r = results.get(0);
            String fullAddress = r.path("formatted").asText(null);
            String city    = firstNonNull(r, "city", "county", "state_district", "state");
            String country = r.path("country").asText(null);

            log.info("Geoapify resolved: {}", fullAddress);
            return new GeocodingResult(fullAddress, city, country);

        } catch (Exception ex) {
            log.error("Geoapify geocoding failed: {}", ex.getMessage());
            return GeocodingResult.empty();
        }
    }

    // ── Nominatim fallback ────────────────────────────────────────────────────

    private GeocodingResult reverseGeocodeNominatim(double latitude, double longitude) {
        String url = String.format(
                "%s?lat=%.7f&lon=%.7f&format=jsonv2&addressdetails=1&zoom=18",
                nominatimUrl, latitude, longitude
        );
        log.info("Calling Nominatim: {}", url);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", userAgent)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Nominatim HTTP {}: {}", response.statusCode(), response.body());
            if (response.statusCode() != 200) return GeocodingResult.empty();

            JsonNode root = objectMapper.readTree(response.body());
            String fullAddress = root.path("display_name").asText(null);
            JsonNode addr = root.path("address");
            String city    = firstNonNull(addr, "road", "suburb", "neighbourhood",
                    "city", "town", "village", "county", "state_district");
            String country = addr.path("country").asText(null);

            if (fullAddress == null || fullAddress.isBlank()) return GeocodingResult.empty();
            return new GeocodingResult(fullAddress, city, country);

        } catch (Exception ex) {
            log.error("Nominatim geocoding failed: {}", ex.getMessage());
            return GeocodingResult.empty();
        }
    }

    private String firstNonNull(JsonNode node, String... keys) {
        for (String key : keys) {
            String val = node.path(key).asText(null);
            if (val != null && !val.isBlank()) return val;
        }
        return null;
    }
}
