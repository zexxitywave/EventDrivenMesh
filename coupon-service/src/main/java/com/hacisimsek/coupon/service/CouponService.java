package com.hacisimsek.coupon.service;

import com.hacisimsek.coupon.model.Coupon;
import com.hacisimsek.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CouponService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_MAX_USES = 5;
    private static final int VALIDITY_DAYS = 7;

    private final CouponRepository couponRepository;

    /**
     * Generate a unique coupon code.
     * Format: CART{discount}-{8 chars} e.g. CART10-K7P2M9QZ
     */
    @Transactional
    public Coupon generateCoupon(int discountPercent, Integer maxUses, Integer validityDays) {
        if (discountPercent < 1 || discountPercent > 100) {
            throw new IllegalArgumentException("discountPercent must be between 1 and 100");
        }

        int uses = maxUses != null && maxUses > 0 ? maxUses : DEFAULT_MAX_USES;
        int days = validityDays != null && validityDays > 0 ? validityDays : VALIDITY_DAYS;

        String code = nextUniqueCode(discountPercent);
        LocalDateTime now = LocalDateTime.now();

        Coupon coupon = Coupon.builder()
                .code(code)
                .discountPercent(discountPercent)
                .expiresAt(now.plusDays(days))
                .usageCount(0)
                .maxUses(uses)
                .active(true)
                .createdAt(now)
                .build();

        return couponRepository.save(coupon);
    }

    private String nextUniqueCode(int discountPercent) {
        String prefix = "CART" + discountPercent + "-";
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = prefix + randomSuffix(8);
            if (couponRepository.findByCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique coupon code");
    }

    private String randomSuffix(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Validate a coupon code. Returns the coupon only when active, unexpired,
     * and still under its usage limit.
     */
    @Transactional(readOnly = true)
    public Optional<Coupon> validateCoupon(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return couponRepository
                .findActiveByCode(code.trim().toUpperCase(), LocalDateTime.now())
                .filter(c -> c.getUsageCount() < c.getMaxUses());
    }

    /**
     * Redeem a coupon: validates, increments usage, and deactivates when exhausted.
     * Returns the coupon if the redemption succeeded.
     */
    @Transactional
    public Optional<Coupon> redeemCoupon(String code) {
        Optional<Coupon> found = validateCoupon(code);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        Coupon coupon = found.get();
        coupon.setUsageCount(coupon.getUsageCount() + 1);
        if (coupon.getUsageCount() >= coupon.getMaxUses()) {
            coupon.setActive(false);
        }
        return Optional.of(couponRepository.save(coupon));
    }

    @Transactional(readOnly = true)
    public boolean existsActive(String code) {
        return validateCoupon(code).isPresent();
    }
}