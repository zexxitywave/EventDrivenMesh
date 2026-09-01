package com.hacisimsek.cart.controller;

import com.hacisimsek.cart.dto.AddToCartRequest;
import com.hacisimsek.cart.dto.UpdateCartItemRequest;
import com.hacisimsek.cart.model.Cart;
import com.hacisimsek.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * View the current user's cart.
     * Creates an empty cart if none exists.
     */
    @GetMapping
    public ResponseEntity<Cart> getCart(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    /**
     * Add a product to the cart.
     * Validates product existence and stock availability.
     */
    @PostMapping("/items")
    public ResponseEntity<Cart> addToCart(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody AddToCartRequest request) {
        return ResponseEntity.ok(cartService.addToCart(userId, request));
    }

    /**
     * Update the quantity of a specific item in the cart.
     */
    @PutMapping("/items/{productId}")
    public ResponseEntity<Cart> updateItemQuantity(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(cartService.updateItemQuantity(userId, productId, request));
    }

    /**
     * Remove a specific item from the cart.
     */
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Cart> removeItem(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID productId) {
        return ResponseEntity.ok(cartService.removeItem(userId, productId));
    }

    /**
     * Clear all items from the cart.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, String>> clearCart(@RequestHeader("X-User-Id") UUID userId) {
        cartService.clearCart(userId);
        return ResponseEntity.ok(Map.of("message", "Cart cleared successfully"));
    }
}
