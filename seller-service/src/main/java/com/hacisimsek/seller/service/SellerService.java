package com.hacisimsek.seller.service;

import com.hacisimsek.seller.dto.OrderSummary;
import com.hacisimsek.seller.dto.ProductSummary;
import com.hacisimsek.seller.dto.SellerAnalyticsResponse;
import com.hacisimsek.seller.dto.SellerProfileResponse;
import com.hacisimsek.seller.dto.SellerRegistrationRequest;
import com.hacisimsek.seller.dto.UpdateSellerProfileRequest;
import com.hacisimsek.seller.model.SellerProfile;
import com.hacisimsek.seller.model.VerificationStatus;
import com.hacisimsek.seller.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerService {

    private final SellerRepository sellerRepository;
    private final RestTemplate restTemplate;
    private final com.hacisimsek.seller.kyc.KycValidator kycValidator;

    private static final String PRODUCT_SERVICE_URL = "http://product-service/api/products";
    private static final String ORDER_SERVICE_URL   = "http://order-service/api/orders";

    // ── Registration ──────────────────────────────────────────────────────────

    @Transactional
    public SellerProfileResponse register(UUID userId, SellerRegistrationRequest request) {
        if (sellerRepository.existsByUserId(userId)) {
            throw new IllegalStateException("A seller profile already exists for this account");
        }
        if (sellerRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Email already registered as a seller: " + request.getEmail());
        }

        SellerProfile profile = SellerProfile.builder()
                .sellerId(UUID.randomUUID())
                .userId(userId)
                .storeName(request.getStoreName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .businessAddress(request.getBusinessAddress())
                .businessRegistrationNumber(request.getBusinessRegistrationNumber())
                .verificationStatus(VerificationStatus.PENDING)
                .build();

        SellerProfile saved = sellerRepository.save(profile);
        log.info("Seller registered: {} (userId={})", saved.getStoreName(), userId);
        return toResponse(saved);
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SellerProfileResponse getProfile(UUID userId) {
        return toResponse(findByUserId(userId));
    }

    @Transactional
    public SellerProfileResponse updateProfile(UUID userId, UpdateSellerProfileRequest request) {
        SellerProfile profile = findByUserId(userId);

        if (request.getStoreName() != null)      profile.setStoreName(request.getStoreName());
        if (request.getPhone() != null)          profile.setPhone(request.getPhone());
        if (request.getBusinessAddress() != null) profile.setBusinessAddress(request.getBusinessAddress());

        return toResponse(sellerRepository.save(profile));
    }

    // ── Products (delegated to product-service) ───────────────────────────────

    /**
     * Returns all products belonging to this seller via product-service.
     * product-service owns the canonical product data; seller-service is a consumer.
     */
    @Transactional(readOnly = true)
    public List<ProductSummary> getSellerProducts(UUID userId) {
        SellerProfile seller = findByUserId(userId);
        requireVerified(seller);

        try {
            ResponseEntity<List<ProductSummary>> response = restTemplate.exchange(
                    PRODUCT_SERVICE_URL + "/seller/" + seller.getSellerId() + "?page=0&size=100",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Could not fetch products from product-service: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Orders (delegated to order-service) ───────────────────────────────────

    /**
     * Returns orders that contain products from this seller.
     * In a full implementation, order-service would filter by sellerId.
     * For now, we delegate to the customer order endpoint.
     */
    @Transactional(readOnly = true)
    public List<OrderSummary> getSellerOrders(UUID userId) {
        SellerProfile seller = findByUserId(userId);
        requireVerified(seller);

        try {
            ResponseEntity<List<OrderSummary>> response = restTemplate.exchange(
                    ORDER_SERVICE_URL + "/seller/" + seller.getSellerId(),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Could not fetch orders from order-service: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Analytics ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SellerAnalyticsResponse getAnalytics(UUID userId) {
        SellerProfile seller = findByUserId(userId);
        requireVerified(seller);

        List<OrderSummary> orders = getSellerOrders(userId);
        List<ProductSummary> products = getSellerProducts(userId);

        long total     = orders.size();
        long pending   = orders.stream().filter(o -> "PENDING".equals(o.getStatus())
                || "INVENTORY_CHECKING".equals(o.getStatus())
                || "PAYMENT_PROCESSING".equals(o.getStatus())).count();
        long completed = orders.stream().filter(o -> "COMPLETED".equals(o.getStatus())
                || "SHIPPED".equals(o.getStatus())).count();
        long cancelled = orders.stream().filter(o -> "CANCELLED".equals(o.getStatus())
                || "FAILED".equals(o.getStatus())).count();

        BigDecimal revenue = orders.stream()
                .filter(o -> "COMPLETED".equals(o.getStatus()) || "SHIPPED".equals(o.getStatus()))
                .map(OrderSummary::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgOrderValue = completed > 0
                ? revenue.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return SellerAnalyticsResponse.builder()
                .sellerId(seller.getSellerId())
                .storeName(seller.getStoreName())
                .totalOrders(total)
                .pendingOrders(pending)
                .completedOrders(completed)
                .cancelledOrders(cancelled)
                .totalRevenue(revenue)
                .totalProductsListed(products.size())
                .averageOrderValue(avgOrderValue)
                .build();
    }

    // ── Verification (admin-only workflow) ────────────────────────────────────

    @Transactional
    public SellerProfileResponse updateVerificationStatus(UUID sellerId,
                                                           VerificationStatus newStatus,
                                                           String notes) {
        SellerProfile profile = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new RuntimeException("Seller not found: " + sellerId));
        profile.setVerificationStatus(newStatus);
        if (notes != null) profile.setVerificationNotes(notes);
        log.info("Seller {} verification updated to {}", sellerId, newStatus);
        return toResponse(sellerRepository.save(profile));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SellerProfile findByUserId(UUID userId) {
        return sellerRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Seller profile not found for user: " + userId));
    }

    private void requireVerified(SellerProfile seller) {
        if (seller.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new IllegalStateException(
                    "Seller account is not verified. Current status: " + seller.getVerificationStatus());
        }
    }

    private SellerProfileResponse toResponse(SellerProfile p) {
        return SellerProfileResponse.builder()
                .sellerId(p.getSellerId())
                .userId(p.getUserId())
                .storeName(p.getStoreName())
                .email(p.getEmail())
                .phone(p.getPhone())
                .businessAddress(p.getBusinessAddress())
                .verificationStatus(p.getVerificationStatus())
                .kycStatus(p.getKycStatus())
                .panMasked(kycValidator.maskPan(p.getPanNumber()))
                // .aadhaarMasked(kycValidator.maskAadhaar(p.getAadhaarNumber()))
                .rating(p.getRating())
                .ratingCount(p.getRatingCount())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
