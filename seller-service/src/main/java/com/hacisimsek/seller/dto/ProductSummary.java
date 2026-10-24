package com.hacisimsek.seller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Minimal product projection returned by product-service for seller's product list.
 */
@Data
public class ProductSummary {
    private UUID id;
    private String name;
    private String sku;
    private BigDecimal price;
    private String status;
    private String imageUrl;
    private BigDecimal averageRating;
    private Integer ratingCount;
}
