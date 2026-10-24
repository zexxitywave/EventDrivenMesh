package com.hacisimsek.wishlist.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Minimal projection of product-service response used to snapshot
 * denormalized product data into the wishlist item.
 */
@Data
public class ProductSnapshot {
    private UUID id;
    private String name;
    private String sku;
    private String imageUrl;
    private BigDecimal price;
    private String status;
}
