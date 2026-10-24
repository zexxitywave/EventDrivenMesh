package com.hacisimsek.cart.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Minimal projection of product-service response.
 * Populated via RestTemplate JSON deserialization.
 */
public class ProductResponse {

    private UUID id;
    private String name;
    private String sku;
    private BigDecimal price;
    private String imageUrl;
    private String status;

    public ProductResponse() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
