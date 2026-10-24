package com.hacisimsek.product.dto;

import com.hacisimsek.product.model.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
// The @Data annotation is part of the Lombok library
// and is used to generate various getter, setter, constructor, and equals, hashCode, and toString methods for a class.
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private UUID id;
    private String name;
    private String description;
    private String sku;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private UUID categoryId;
    private String categoryName;
    private UUID sellerId;
    private ProductStatus status;
    private String imageUrl;
    private List<String> additionalImages;
    private String brand;
    private Integer weightGrams;
    private BigDecimal averageRating;
    private Integer ratingCount;
    private Instant createdAt;
    private Instant updatedAt;
}
