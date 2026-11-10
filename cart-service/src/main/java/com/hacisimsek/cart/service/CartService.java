package com.hacisimsek.cart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.cart.dto.AbandonedCartResponse;
import com.hacisimsek.cart.dto.AddToCartRequest;
import com.hacisimsek.cart.dto.CheckoutItemRequest;
import com.hacisimsek.cart.dto.CheckoutRequest;
import com.hacisimsek.cart.dto.CheckoutResponse;
import com.hacisimsek.cart.dto.CouponResponse;
import com.hacisimsek.cart.dto.ProductResponse;
import com.hacisimsek.cart.dto.UpdateCartItemRequest;
import com.hacisimsek.cart.model.Cart;
import com.hacisimsek.cart.model.CartItem;
import com.hacisimsek.cart.model.ReminderStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CartService {

    private final RedisTemplate<String, Object> cartRedisTemplate;
    private final RestTemplate restTemplate;
    private final RestTemplate plainRestTemplate;
    private final ObjectMapper objectMapper;

    public CartService(RedisTemplate<String, Object> cartRedisTemplate,
                       @Qualifier("restTemplate") RestTemplate restTemplate,
                       @Qualifier("plainRestTemplate") RestTemplate plainRestTemplate,
                       @Qualifier("cartObjectMapper") ObjectMapper objectMapper) {
        this.cartRedisTemplate = cartRedisTemplate;
        this.restTemplate = restTemplate;
        this.plainRestTemplate = plainRestTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("${app.cart.ttl-seconds}")
    private long cartTtlSeconds;

    @Value("${app.coupon.base-url:http://localhost:8097}")
    private String couponBaseUrl;

    private static final String CART_KEY_PREFIX  = "cart:";
    private static final String PRODUCT_BASE_URL = "http://product-service/api/v1/products";
    private static final String INVENTORY_CHECK_URL =
            "http://inventory-service/api/v1/inventory/check?productId={productId}&quantity={quantity}";
    private static final String ORDER_SERVICE_URL = "http://order-service/api/v1/orders";

    // ── Add to Cart ───────────────────────────────────────────────────────────

    public Cart addToCart(UUID userId, AddToCartRequest request) {
        ProductResponse product = fetchProduct(request.getProductId());

        if (!checkStock(request.getProductId(), request.getQuantity())) {
            throw new RuntimeException("Insufficient stock for product: " + request.getProductId());
        }

        Cart cart = getOrCreateCart(userId);

        Optional<CartItem> existing = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(request.getProductId()))
                .findFirst();

        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQty = item.getQuantity() + request.getQuantity();
            if (!checkStock(request.getProductId(), newQty)) {
                throw new RuntimeException("Not enough stock. Requested total: " + newQty);
            }
            item.setQuantity(newQty);
            item.setTotalPrice(product.getPrice().multiply(BigDecimal.valueOf(newQty)));
        } else {
            cart.getItems().add(CartItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .sku(product.getSku())
                    .imageUrl(product.getImageUrl())
                    .quantity(request.getQuantity())
                    .price(product.getPrice())
                    .totalPrice(product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())))
                    .build());
        }

        cart.recalculate();
        markActive(cart);
        return saveCart(cart);
    }

    // ── Update Quantity ───────────────────────────────────────────────────────

    public Cart updateItemQuantity(UUID userId, UUID productId, UpdateCartItemRequest request) {
        Cart cart = getCartOrThrow(userId);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Item not in cart: " + productId));

        if (!checkStock(productId, request.getQuantity())) {
            throw new RuntimeException("Not enough stock for quantity: " + request.getQuantity());
        }

        ProductResponse product = fetchProduct(productId);
        item.setQuantity(request.getQuantity());
        item.setPrice(product.getPrice());
        item.setTotalPrice(product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())));

        cart.recalculate();
        markActive(cart);
        return saveCart(cart);
    }

    // ── Remove Item ───────────────────────────────────────────────────────────

    public Cart removeItem(UUID userId, UUID productId) {
        Cart cart = getCartOrThrow(userId);
        if (!cart.getItems().removeIf(i -> i.getProductId().equals(productId))) {
            throw new RuntimeException("Item not in cart: " + productId);
        }
        cart.recalculate();
        markActive(cart);
        return saveCart(cart);
    }

    // ── Coupons ───────────────────────────────────────────────────────────────

    /**
     * Validate a coupon through coupon-service and apply the discount to this
     * cart. The cart total is recalculated immediately.
     */
    public Cart applyCoupon(UUID userId, String code) {
        if (code == null || code.isBlank()) {
            throw new RuntimeException("Coupon code is required");
        }

        Cart cart = getCartOrThrow(userId);
        CouponResponse coupon = validateCoupon(code.trim().toUpperCase());

        cart.setAppliedCouponCode(coupon.getCode());
        cart.setCouponDiscountPercent(coupon.getDiscountPercent());
        cart.recalculate();
        markActive(cart);
        log.info("Applied coupon {} ({}% off) to cart for user {}",
                coupon.getCode(), coupon.getDiscountPercent(), userId);
        return saveCart(cart);
    }

    /** Remove an applied coupon and restore the cart's undiscounted total. */
    public Cart removeCoupon(UUID userId) {
        Cart cart = getCartOrThrow(userId);
        cart.setAppliedCouponCode(null);
        cart.setCouponDiscountPercent(0);
        cart.recalculate();
        markActive(cart);
        log.info("Removed coupon from cart for user {}", userId);
        return saveCart(cart);
    }

    private CouponResponse validateCoupon(String code) {
        try {
            ResponseEntity<CouponResponse> response = plainRestTemplate.getForEntity(
                    couponBaseUrl + "/api/v1/coupons/validate?code={code}",
                    CouponResponse.class, code);
            CouponResponse coupon = response.getBody();
            if (coupon == null) {
                throw new RuntimeException("Invalid or expired coupon: " + code);
            }
            return coupon;
        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("Invalid or expired coupon: " + code);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not reach coupon-service: " + e.getMessage());
        }
    }

    /**
     * Consume one use of an applied coupon after the order was created.
     * Best effort: the order is already placed, so a coupon-service hiccup must
     * not turn a successful checkout into an error.
     */
    private void redeemCoupon(String code) {
        try {
            plainRestTemplate.postForEntity(
                    couponBaseUrl + "/api/v1/coupons/redeem?code={code}",
                    null, CouponResponse.class, code);
            log.info("Redeemed coupon {} at checkout", code);
        } catch (Exception e) {
            log.warn("Order created but coupon {} could not be redeemed: {}", code, e.getMessage());
        }
    }

    // ── View Cart ─────────────────────────────────────────────────────────────

    public Cart getCart(UUID userId) {
        return getOrCreateCart(userId);
    }

    // ── Clear Cart ────────────────────────────────────────────────────────────

    public void clearCart(UUID userId) {
        cartRedisTemplate.delete(cartKey(userId));
        log.info("Cart cleared for user: {}", userId);
    }

    // ── Abandoned-cart recovery (internal API used by n8n) ───────────────────

    /**
     * Scans all carts in Redis and returns the ones that are abandoned.
     *
     * <p>A cart is abandoned when it still has items, its last activity
     * ({@code updatedAt}) is older than {@code olderThanMinutes}, and — when a
     * stage filter is supplied — its reminder stage matches.</p>
     *
     * <p>This uses Redis {@code KEYS}, which is fine for a demo dataset but
     * O(N). A production implementation should maintain a Redis sorted set
     * (score = last activity) or a small Postgres/Postgres-outbox table.</p>
     */
    public List<AbandonedCartResponse> getAbandonedCarts(int olderThanMinutes,
                                                         ReminderStage stage,
                                                         int limit) {
        Instant cutoff = Instant.now().minusSeconds(Math.max(0, olderThanMinutes) * 60L);
        Set<String> keys = cartRedisTemplate.keys(CART_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        List<AbandonedCartResponse> abandoned = new ArrayList<>();
        for (String key : keys) {
            Object raw = cartRedisTemplate.opsForValue().get(key);
            if (raw == null) {
                continue;
            }

            Cart cart;
            try {
                cart = objectMapper.convertValue(raw, Cart.class);
            } catch (IllegalArgumentException e) {
                log.warn("Skipping unreadable cart at key {}: {}", key, e.getMessage());
                continue;
            }

            if (cart.getItems() == null || cart.getItems().isEmpty()) {
                continue;
            }
            if (cart.getUpdatedAt() == null || cart.getUpdatedAt().isAfter(cutoff)) {
                continue;
            }

            ReminderStage cartStage = cart.getReminderStage() != null
                    ? cart.getReminderStage()
                    : ReminderStage.NONE;
            if (stage != null && cartStage != stage) {
                continue;
            }

            abandoned.add(AbandonedCartResponse.builder()
                    .cartId(cart.getCartId())
                    .userId(cart.getUserId())
                    .items(cart.getItems())
                    .itemCount(cart.getTotalItemCount())
                    .grandTotal(cart.getGrandTotal())
                    .createdAt(cart.getCreatedAt())
                    .updatedAt(cart.getUpdatedAt())
                    .minutesIdle(Duration.between(cart.getUpdatedAt(), Instant.now()).toMinutes())
                    .reminderStage(cartStage)
                    .lastReminderAt(cart.getLastReminderAt())
                    .build());
        }

        // Oldest first: the customer who has waited longest gets reminded first.
        abandoned.sort(Comparator.comparing(AbandonedCartResponse::getUpdatedAt));
        if (limit > 0 && abandoned.size() > limit) {
            return abandoned.subList(0, limit);
        }
        return abandoned;
    }

    /**
     * Records that the n8n workflow sent a reminder/coupon for this cart.
     * Preserves the remaining Redis TTL so workflow state does not extend the
     * cart's life.
     */
    public Cart markReminderStage(UUID userId, ReminderStage stage) {
        Cart cart = getCartOrThrow(userId);
        ReminderStage newStage = stage != null ? stage : ReminderStage.NONE;
        cart.setReminderStage(newStage);
        cart.setLastReminderAt(newStage == ReminderStage.NONE ? null : Instant.now());

        savePreservingTtl(cart);
        log.info("Cart reminder stage for user {} set to {}", userId, newStage);
        return cart;
    }

    /**
     * Test helper: pretend the cart has been idle for {@code minutes} and put
     * it back to {@link ReminderStage#NONE}. Lets you exercise the n8n workflow
     * immediately instead of waiting for the real abandonment window.
     * Not exposed through the API Gateway.
     */
    public Cart simulateIdle(UUID userId, long minutes) {
        Cart cart = getCartOrThrow(userId);
        cart.setUpdatedAt(Instant.now().minusSeconds(Math.max(0, minutes) * 60));
        cart.setReminderStage(ReminderStage.NONE);
        cart.setLastReminderAt(null);

        savePreservingTtl(cart);
        log.info("Simulated {} minutes of cart idleness for user {}", minutes, userId);
        return cart;
    }

    /** Any genuine cart activity starts a new abandoned-cart period. */
    private void markActive(Cart cart) {
        cart.setReminderStage(ReminderStage.NONE);
        cart.setLastReminderAt(null);
    }

    /** Writes the cart without extending its Redis TTL. */
    private void savePreservingTtl(Cart cart) {
        String key = cartKey(cart.getUserId());
        Long remainingSeconds = cartRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        long ttl = (remainingSeconds != null && remainingSeconds > 0)
                ? remainingSeconds
                : cartTtlSeconds;
        cartRedisTemplate.opsForValue().set(key, cart, ttl, TimeUnit.SECONDS);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ProductResponse fetchProduct(UUID productId) {
        try {
            ResponseEntity<ProductResponse> response = restTemplate.getForEntity(
                    PRODUCT_BASE_URL + "/" + productId, ProductResponse.class);
            ProductResponse product = response.getBody();
            if (product == null) {
                throw new RuntimeException("Product not found: " + productId);
            }
            if ("INACTIVE".equals(product.getStatus()) || "DISCONTINUED".equals(product.getStatus())) {
                throw new RuntimeException("Product is not available for purchase: " + productId);
            }
            return product;
        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("Product not found: " + productId);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not reach product-service: " + e.getMessage());
        }
    }

    private boolean checkStock(UUID productId, int quantity) {
        try {
            Boolean available = restTemplate.getForObject(
                    INVENTORY_CHECK_URL, Boolean.class, productId, quantity);
            return Boolean.TRUE.equals(available);
        } catch (org.springframework.web.client.RestClientException |
                 IllegalArgumentException e) {
            log.warn("Could not check stock for product {}: {}", productId, e.getMessage());
            // Fail open — allow adding to cart if inventory-service is unreachable
            return true;
        }
    }

    private Cart getOrCreateCart(UUID userId) {
        Object raw = cartRedisTemplate.opsForValue().get(cartKey(userId));
        if (raw == null) {
            return Cart.builder()
                    .cartId(UUID.randomUUID())
                    .userId(userId)
                    .grandTotal(BigDecimal.ZERO)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
        }
        return objectMapper.convertValue(raw, Cart.class);
    }

    private Cart getCartOrThrow(UUID userId) {
        Object raw = cartRedisTemplate.opsForValue().get(cartKey(userId));
        if (raw == null) {
            throw new RuntimeException("Cart not found for user: " + userId);
        }
        return objectMapper.convertValue(raw, Cart.class);
    }

    private Cart saveCart(Cart cart) {
        cartRedisTemplate.opsForValue().set(
                cartKey(cart.getUserId()), cart, cartTtlSeconds, TimeUnit.SECONDS);
        log.debug("Cart saved — user: {}, items: {}, total: {}",
                cart.getUserId(), cart.getItems().size(), cart.getGrandTotal());
        return cart;
    }

    // ── Checkout ──────────────────────────────────────────────────────────────

    public CheckoutResponse checkout(UUID userId) {
        Cart cart = getCartOrThrow(userId);
        if (cart.getItems().isEmpty()) {
            throw new RuntimeException("Cart is empty for user: " + userId);
        }

        // Re-validate the coupon at checkout time: it may have expired or been
        // fully redeemed since it was applied to the cart.
        if (cart.getAppliedCouponCode() != null && !cart.getAppliedCouponCode().isBlank()) {
            CouponResponse coupon = validateCoupon(cart.getAppliedCouponCode());
            cart.setCouponDiscountPercent(coupon.getDiscountPercent());
            cart.recalculate();
        }

        CheckoutRequest orderRequest = new CheckoutRequest();
        orderRequest.setCustomerId(userId);
        orderRequest.setItems(cart.getItems().stream()
                .map(item -> new CheckoutItemRequest(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getPrice()))
                .collect(java.util.stream.Collectors.toList()));
        orderRequest.setCouponCode(cart.getAppliedCouponCode());
        orderRequest.setDiscountAmount(cart.getDiscountAmount());

        try {
            ResponseEntity<CheckoutResponse> response = restTemplate.postForEntity(
                    ORDER_SERVICE_URL, orderRequest, CheckoutResponse.class);
            CheckoutResponse order = response.getBody();
            if (order == null) {
                throw new RuntimeException("Order service returned empty response");
            }
            if (cart.getAppliedCouponCode() != null && !cart.getAppliedCouponCode().isBlank()) {
                redeemCoupon(cart.getAppliedCouponCode());
            }
            clearCart(userId);
            log.info("Checkout completed for user: {}, orderId: {}", userId, order.getOrderId());
            return order;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw new RuntimeException("Order creation failed: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException("Order service is unavailable: " + e.getMessage());
        }
    }

    private String cartKey(UUID userId) {
        return CART_KEY_PREFIX + userId;
    }
}
