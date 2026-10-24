package com.hacisimsek.cart.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

public class CartItem implements Serializable {

    private UUID productId;
    private String productName;
    private String sku;
    private String imageUrl;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal totalPrice;

    public CartItem() {}

    public CartItem(UUID productId, String productName, String sku, String imageUrl,
                    Integer quantity, BigDecimal price, BigDecimal totalPrice) {
        this.productId = productId;
        this.productName = productName;
        this.sku = sku;
        this.imageUrl = imageUrl;
        this.quantity = quantity;
        this.price = price;
        this.totalPrice = totalPrice;
    }

    // Builder pattern
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private UUID productId;
        private String productName;
        private String sku;
        private String imageUrl;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal totalPrice;

        public Builder productId(UUID productId) { this.productId = productId; return this; }
        public Builder productName(String productName) { this.productName = productName; return this; }
        public Builder sku(String sku) { this.sku = sku; return this; }
        public Builder imageUrl(String imageUrl) { this.imageUrl = imageUrl; return this; }
        public Builder quantity(Integer quantity) { this.quantity = quantity; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }
        public Builder totalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; return this; }
        public CartItem build() {
            return new CartItem(productId, productName, sku, imageUrl, quantity, price, totalPrice);
        }
    }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
}
