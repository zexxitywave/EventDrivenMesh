package com.hacisimsek.cart.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Subset of coupon-service's response used by the cart when applying a coupon.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CouponResponse {
    private Long id;
    private String code;
    private int discountPercent;
    private LocalDateTime expiresAt;
    private int usageCount;
    private int maxUses;
    private boolean active;
}