package com.hacisimsek.product.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    /** Stock Keeping Unit — unique product code */
    @Column(unique = true, nullable = false)
    private String sku;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Optional compare-at price for showing discounts */
    @Column(precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /**
     * Seller / merchant UUID — maps to a user with ROLE_SELLER in auth-service.
     * We store just the UUID, no FK to another service.
     */
    @Column(nullable = false)
    private UUID sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    /** Primary image URL (stored in S3 or any CDN — we just hold the URL). */
    private String imageUrl;

    /** Additional image URLs */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url")
    @Builder.Default
    private List<String> additionalImages = new ArrayList<>();

    /** Brand name */
    private String brand;

    /** Weight in grams (used for shipping cost calculation) */
    private Integer weightGrams;

    /** Average rating 0.0 – 5.0, updated by review-service events */
    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO;

    /** Total number of ratings */
    @Builder.Default
    private Integer ratingCount = 0;

    /*
     * The 'embedding' vector(768) column is intentionally NOT mapped here.
     * Hibernate 6's generic binding sends PGobject as bytea, which Postgres
     * rejects for a vector column. We read/write it via native SQL:
     *   - write: UPDATE products SET embedding = cast(:vec AS vector)
     *   - read : native SELECT ordering by embedding <=> cast(:vec AS vector)
     */

    @Column(updatable = false)
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) this.status = ProductStatus.ACTIVE;
        if (this.averageRating == null) this.averageRating = BigDecimal.ZERO;
        if (this.ratingCount == null)   this.ratingCount = 0;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
