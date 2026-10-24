package com.hacisimsek.wishlist.service;

import com.hacisimsek.wishlist.dto.AddToWishlistRequest;
import com.hacisimsek.wishlist.dto.ProductSnapshot;
import com.hacisimsek.wishlist.dto.WishlistItemResponse;
import com.hacisimsek.wishlist.model.WishlistItem;
import com.hacisimsek.wishlist.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final RestTemplate restTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String PRODUCT_BASE_URL  = "http://product-service/api/products";
    private static final String CART_ADD_URL       = "http://cart-service/api/cart/items";
    private static final String WISHLIST_TOPIC     = "wishlist-events";

    // ── Add to Wishlist ───────────────────────────────────────────────────────

    public WishlistItemResponse addToWishlist(UUID userId, AddToWishlistRequest request) {
        UUID productId = request.getProductId();

        // Idempotent duplicate check — DB compound index also enforces this
        if (wishlistRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new IllegalStateException("Product " + productId + " is already in your wishlist");
        }

        // Validate product exists and is active
        ProductSnapshot product = fetchProduct(productId);

        WishlistItem item = WishlistItem.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .productId(productId)
                .productName(product.getName())
                .productSku(product.getSku())
                .productImageUrl(product.getImageUrl())
                .build();

        WishlistItem saved = wishlistRepository.save(item);
        log.info("Product {} added to wishlist for user {}", productId, userId);

        // Publish event so analytics / recommendation services can consume it
        kafkaTemplate.send(WISHLIST_TOPIC, Map.of(
                "type",      "PRODUCT_WISHLISTED",
                "userId",    userId.toString(),
                "productId", productId.toString()
        ));

        return toResponse(saved);
    }

    // ── Remove from Wishlist ──────────────────────────────────────────────────

    public void removeFromWishlist(UUID userId, UUID productId) {
        if (!wishlistRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new IllegalStateException("Product " + productId + " is not in your wishlist");
        }
        wishlistRepository.deleteByUserIdAndProductId(userId, productId);
        log.info("Product {} removed from wishlist for user {}", productId, userId);
    }

    // ── View Wishlist ─────────────────────────────────────────────────────────

    public List<WishlistItemResponse> getWishlist(UUID userId) {
        return wishlistRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Move to Cart ──────────────────────────────────────────────────────────

    /**
     * Calls cart-service to add the item, then removes it from the wishlist.
     * Uses a best-effort approach: if cart-service call fails, the wishlist item is NOT removed.
     */
    public void moveToCart(UUID userId, UUID productId) {
        WishlistItem item = wishlistRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new IllegalStateException("Product " + productId + " is not in your wishlist"));

        try {
            // POST to cart-service — X-User-Id header passed for service-to-service auth
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                    Map.of("productId", productId, "quantity", 1),
                    buildUserIdHeader(userId)
            );
            restTemplate.postForObject(CART_ADD_URL, request, Object.class);
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Failed to add item to cart: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException("Cart service is unavailable: " + e.getMessage());
        }

        wishlistRepository.delete(item);
        log.info("Product {} moved from wishlist to cart for user {}", productId, userId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ProductSnapshot fetchProduct(UUID productId) {
        try {
            ProductSnapshot product = restTemplate.getForObject(
                    PRODUCT_BASE_URL + "/" + productId, ProductSnapshot.class);
            if (product == null) {
                throw new RuntimeException("Product not found: " + productId);
            }
            if ("INACTIVE".equals(product.getStatus()) || "DISCONTINUED".equals(product.getStatus())) {
                throw new IllegalStateException("Product is not available: " + productId);
            }
            return product;
        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("Product not found: " + productId);
        }
    }

    private HttpHeaders buildUserIdHeader(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private WishlistItemResponse toResponse(WishlistItem item) {
        return WishlistItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .productImageUrl(item.getProductImageUrl())
                .productSku(item.getProductSku())
                .addedAt(item.getAddedAt())
                .build();
    }
}
