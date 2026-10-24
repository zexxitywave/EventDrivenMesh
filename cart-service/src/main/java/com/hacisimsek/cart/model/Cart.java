package com.hacisimsek.cart.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Cart implements Serializable {

    private UUID cartId;
    private UUID userId;
    private List<CartItem> items = new ArrayList<>();
    private BigDecimal grandTotal = BigDecimal.ZERO;
    private Instant createdAt;
    private Instant updatedAt;

    public Cart() {}

    public Cart(UUID cartId, UUID userId, List<CartItem> items,
                BigDecimal grandTotal, Instant createdAt, Instant updatedAt) {
        this.cartId = cartId;
        this.userId = userId;
        this.items = items != null ? items : new ArrayList<>();
        this.grandTotal = grandTotal != null ? grandTotal : BigDecimal.ZERO;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private UUID cartId;
        private UUID userId;
        private List<CartItem> items = new ArrayList<>();
        private BigDecimal grandTotal = BigDecimal.ZERO;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder cartId(UUID cartId) { this.cartId = cartId; return this; }
        public Builder userId(UUID userId) { this.userId = userId; return this; }
        public Builder items(List<CartItem> items) { this.items = items; return this; }
        public Builder grandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Cart build() {
            return new Cart(cartId, userId, items, grandTotal, createdAt, updatedAt);
        }
    }

    /** Recalculates grandTotal from all items. */
    public void recalculate() {
        this.grandTotal = items.stream()
                .map(CartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.updatedAt = Instant.now();
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public int getTotalItemCount() {
        return items.stream().mapToInt(CartItem::getQuantity).sum();
    }

    public UUID getCartId() { return cartId; }
    public void setCartId(UUID cartId) { this.cartId = cartId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }

    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
