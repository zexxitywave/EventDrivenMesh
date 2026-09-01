package com.hacisimsek.wishlist.controller;

import com.hacisimsek.wishlist.dto.AddToWishlistRequest;
import com.hacisimsek.wishlist.dto.WishlistItemResponse;
import com.hacisimsek.wishlist.service.WishlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    /** Add a product to the authenticated user's wishlist. Returns 409 on duplicate. */
    @PostMapping("/add")
    public ResponseEntity<WishlistItemResponse> addToWishlist(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody AddToWishlistRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(wishlistService.addToWishlist(userId, request));
    }

    /** Remove a product from the wishlist. */
    @DeleteMapping("/remove/{productId}")
    public ResponseEntity<Map<String, String>> removeFromWishlist(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID productId) {
        wishlistService.removeFromWishlist(userId, productId);
        return ResponseEntity.ok(Map.of("message", "Product removed from wishlist"));
    }

    /** Get the full wishlist for a user. */
    @GetMapping("/{userId}")
    public ResponseEntity<List<WishlistItemResponse>> getWishlist(@PathVariable UUID userId) {
        return ResponseEntity.ok(wishlistService.getWishlist(userId));
    }

    /** Move a wishlist item to the cart, then remove it from the wishlist. */
    @PostMapping("/move-to-cart/{productId}")
    public ResponseEntity<Map<String, String>> moveToCart(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID productId) {
        wishlistService.moveToCart(userId, productId);
        return ResponseEntity.ok(Map.of("message", "Product moved to cart successfully"));
    }
}
