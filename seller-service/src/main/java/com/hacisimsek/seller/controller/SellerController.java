package com.hacisimsek.seller.controller;

import com.hacisimsek.seller.dto.OrderSummary;
import com.hacisimsek.seller.dto.ProductSummary;
import com.hacisimsek.seller.dto.SellerAnalyticsResponse;
import com.hacisimsek.seller.dto.SellerProfileResponse;
import com.hacisimsek.seller.dto.SellerRegistrationRequest;
import com.hacisimsek.seller.dto.UpdateSellerProfileRequest;
import com.hacisimsek.seller.service.SellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/seller")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    /**
     * Register the authenticated user as a seller.
     * userId is injected by the API Gateway via X-User-Id header.
     */
    @PostMapping("/register")
    public ResponseEntity<SellerProfileResponse> register(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody SellerRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sellerService.register(userId, request));
    }

    /** Get the authenticated seller's own profile. */
    @GetMapping("/profile")
    public ResponseEntity<SellerProfileResponse> getProfile(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(sellerService.getProfile(userId));
    }

    /** Update store name, phone, or business address. */
    @PutMapping("/profile")
    public ResponseEntity<SellerProfileResponse> updateProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateSellerProfileRequest request) {
        return ResponseEntity.ok(sellerService.updateProfile(userId, request));
    }

    /**
     * Get all products listed by this seller.
     * Delegates to product-service via Eureka-discovered RestTemplate.
     * Requires VERIFIED status.
     */
    @GetMapping("/products")
    public ResponseEntity<List<ProductSummary>> getSellerProducts(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(sellerService.getSellerProducts(userId));
    }

    /**
     * Get orders that contain this seller's products.
     * Delegates to order-service. Requires VERIFIED status.
     */
    @GetMapping("/orders")
    public ResponseEntity<List<OrderSummary>> getSellerOrders(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(sellerService.getSellerOrders(userId));
    }

    /**
     * Sales analytics â€” total orders, revenue, average order value, product count.
     * Requires VERIFIED status.
     */
    @GetMapping("/analytics")
    public ResponseEntity<SellerAnalyticsResponse> getAnalytics(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(sellerService.getAnalytics(userId));
    }
}
