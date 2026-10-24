package com.hacisimsek.cart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.cart.dto.AddToCartRequest;
import com.hacisimsek.cart.dto.CheckoutItemRequest;
import com.hacisimsek.cart.dto.CheckoutRequest;
import com.hacisimsek.cart.dto.CheckoutResponse;
import com.hacisimsek.cart.dto.ProductResponse;
import com.hacisimsek.cart.dto.UpdateCartItemRequest;
import com.hacisimsek.cart.model.Cart;
import com.hacisimsek.cart.model.CartItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CartService {

    private final RedisTemplate<String, Object> cartRedisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CartService(RedisTemplate<String, Object> cartRedisTemplate,
                       RestTemplate restTemplate,
                       @Qualifier("cartObjectMapper") ObjectMapper objectMapper) {
        this.cartRedisTemplate = cartRedisTemplate;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("${app.cart.ttl-seconds}")
    private long cartTtlSeconds;

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
        return saveCart(cart);
    }

    // ── Remove Item ───────────────────────────────────────────────────────────

    public Cart removeItem(UUID userId, UUID productId) {
        Cart cart = getCartOrThrow(userId);
        if (!cart.getItems().removeIf(i -> i.getProductId().equals(productId))) {
            throw new RuntimeException("Item not in cart: " + productId);
        }
        cart.recalculate();
        return saveCart(cart);
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

        CheckoutRequest orderRequest = new CheckoutRequest();
        orderRequest.setCustomerId(userId);
        orderRequest.setItems(cart.getItems().stream()
                .map(item -> new CheckoutItemRequest(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getPrice()))
                .collect(java.util.stream.Collectors.toList()));

        try {
            ResponseEntity<CheckoutResponse> response = restTemplate.postForEntity(
                    ORDER_SERVICE_URL, orderRequest, CheckoutResponse.class);
            CheckoutResponse order = response.getBody();
            if (order == null) {
                throw new RuntimeException("Order service returned empty response");
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
