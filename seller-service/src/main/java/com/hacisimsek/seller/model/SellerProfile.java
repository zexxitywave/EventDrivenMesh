package com.hacisimsek.seller.model;

import com.hacisimsek.seller.model.VerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Seller profile.
 * The seller's userId is the same UUID issued by auth-service.
 * sellerId is a separate surrogate key for the seller entity itself.
 */
@Entity
@Table(name = "seller_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerProfile {

    @Id
    private UUID sellerId;

    /** References the user UUID from auth-service — unique per seller. */
    @Column(unique = true, nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String storeName;

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;

    private String businessAddress;

    /** Tax / business registration number. */
    private String businessRegistrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus;

    /** Average seller rating 0.0–5.0, updated by review aggregation. */
    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal rating = BigDecimal.ZERO;

    /** Total number of ratings received. */
    @Builder.Default
    private Integer ratingCount = 0;

    /** Notes from admin during verification review. */
    @Column(length = 1000)
    private String verificationNotes;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.verificationStatus == null) this.verificationStatus = VerificationStatus.PENDING;
        if (this.rating == null)             this.rating = BigDecimal.ZERO;
        if (this.ratingCount == null)        this.ratingCount = 0;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
