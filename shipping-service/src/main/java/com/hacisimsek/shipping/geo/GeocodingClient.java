package com.hacisimsek.shipping.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Free, key-less geocoding via OpenStreetMap's Nominatim.
 * Returns a real lat/lng for a real address — never throws, so a geocoding
 * outage cannot break the shipping saga.
 */
@Component
@Slf4j
public class GeocodingClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String userAgent;

    public GeocodingClient(ObjectMapper objectMapper,
                           @Value("${shipping.geocoding.base-url:https://nominatim.openstreetmap.org}") String baseUrl,
                           @Value("${shipping.geocoding.user-agent:EventDrivenMesh-shipping/0.1 (dev)}") String userAgent,
                           @Value("${shipping.geocoding.timeout-ms:5000}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.userAgent = userAgent;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public record GeoPoint(double lat, double lng, String displayName) {}

    public Optional<GeoPoint> geocode(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        for (String candidate : buildCandidates(address)) {
            Optional<GeoPoint> hit = tryQuery(candidate);
            if (hit.isPresent()) {
                log.info("Geocoded '{}' via '{}' -> {}, {} ({})",
                        address, candidate, hit.get().lat(), hit.get().lng(), hit.get().displayName());
                return hit;
            }
        }
        log.warn("Geocoding gave up on address '{}' (all {} fallbacks empty)", address,
                buildCandidates(address).size());
        return Optional.empty();
    }

    /**
     * OSM coverage has no house-number-level data for many regions, so a single
     * exact query fails for real addresses like "12 MG Road, ... Bengaluru".
     * Keep the address genuine but progressively widen the query until the
     * street- or city-level point is found.
     */
    private List<String> buildCandidates(String address) {
        List<String> tokens = new ArrayList<>();
        for (String part : address.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                tokens.add(trimmed);
            }
        }
        List<String> candidates = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            candidates.add(String.join(",", tokens.subList(i, tokens.size())));
        }
        if (tokens.size() >= 2 && tokens.get(0).matches(".*\\d.*")) {
            String streetWithoutNumber = tokens.get(0).replaceFirst("^\\d+\\s*", "");
            if (!streetWithoutNumber.equals(tokens.get(0))) {
                List<String> widened = new ArrayList<>(tokens);
                widened.set(0, streetWithoutNumber);
                String joined = String.join(",", widened);
                if (!candidates.contains(joined)) {
                    candidates.add(joined);
                }
            }
        }
        return candidates;
    }

    private Optional<GeoPoint> tryQuery(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri = URI.create(baseUrl + "/search?format=json&limit=1&q=" + encoded);

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Geocoding HTTP {} for query '{}'", response.statusCode(), query);
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray() || root.isEmpty()) {
                return Optional.empty();
            }

            JsonNode best = root.get(0);
            return Optional.of(new GeoPoint(
                    Double.parseDouble(best.path("lat").asText()),
                    Double.parseDouble(best.path("lon").asText()),
                    best.path("display_name").asText()));
        } catch (Exception e) {
            log.warn("Geocoding failed for query '{}': {}", query, e.getMessage());
            return Optional.empty();
        }
    }
}