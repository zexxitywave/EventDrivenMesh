package com.locationservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Persisted record of a single location capture event.
 * One row = one time a user clicked "Detect My Location".
 */
@Entity
@Table(name = "user_locations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** WGS-84 latitude supplied by the browser (-90 to +90). */
    @Column(nullable = false)
    private Double latitude;

    /** WGS-84 longitude supplied by the browser (-180 to +180). */
    @Column(nullable = false)
    private Double longitude;

    /** Browser-reported coordinate accuracy in metres (optional). */
    private Double accuracy;

    /** Human-readable address resolved via reverse geocoding (optional). */
    @Column(length = 512)
    private String address;

    /** City portion of the resolved address (optional). */
    private String city;

    /** Country portion of the resolved address (optional). */
    private String country;

    /** IP address of the HTTP request (for audit purposes). */
    @Column(length = 64)
    private String ipAddress;

    /** Row creation timestamp – set automatically before insert. */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
