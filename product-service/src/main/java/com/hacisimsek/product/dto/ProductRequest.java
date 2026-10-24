package com.hacisimsek.product.dto;

import com.hacisimsek.product.model.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @NotBlank(message = "SKU is required")
    private String sku;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    private BigDecimal originalPrice;

    private UUID categoryId;

    /** Seller UUID — injected from X-User-Id header in the controller */
    private UUID sellerId;

    private ProductStatus status;

    private String imageUrl;

    private List<String> additionalImages;

    private String brand;

    private Integer weightGrams;
}
