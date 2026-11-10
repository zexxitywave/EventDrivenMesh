package com.hacisimsek.coupon.controller;

import com.hacisimsek.coupon.model.Coupon;
import com.hacisimsek.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /**
     * Generate a coupon. Both GET and POST are supported so n8n can call it
     * with either method.
     *
     * <pre>POST /api/v1/coupons/generate?discountPercent=10&maxUses=5&validityDays=7</pre>
     */
    @GetMapping("/generate")
    public ResponseEntity<Coupon> generateGet(
            @RequestParam int discountPercent,
            @RequestParam(required = false) Integer maxUses,
            @RequestParam(required = false) Integer validityDays) {
        return generate(discountPercent, maxUses, validityDays);
    }

    @PostMapping("/generate")
    public ResponseEntity<Coupon> generatePost(
            @RequestParam int discountPercent,
            @RequestParam(required = false) Integer maxUses,
            @RequestParam(required = false) Integer validityDays) {
        return generate(discountPercent, maxUses, validityDays);
    }

    private ResponseEntity<Coupon> generate(int discountPercent, Integer maxUses, Integer validityDays) {
        try {
            Coupon coupon = couponService.generateCoupon(discountPercent, maxUses, validityDays);
            return ResponseEntity.status(HttpStatus.CREATED).body(coupon);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Validate a code without consuming it.
     * <pre>GET /api/v1/coupons/validate?code=CART10-K7P2M9QZ</pre>
     */
    @GetMapping("/validate")
    public ResponseEntity<Coupon> validate(@RequestParam String code) {
        return couponService.validateCoupon(code)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Redeem a code (increments usage, deactivates at maxUses).
     * <pre>POST /api/v1/coupons/redeem?code=CART10-K7P2M9QZ</pre>
     */
    @PostMapping("/redeem")
    public ResponseEntity<Coupon> redeem(@RequestParam String code) {
        return couponService.redeemCoupon(code)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build());
    }

    /**
     * Cheap existence check for other services / n8n.
     * <pre>GET /api/v1/coupons/exists?code=CART10-K7P2M9QZ</pre>
     */
    @GetMapping("/exists")
    public ResponseEntity<Boolean> exists(@RequestParam String code) {
        return ResponseEntity.ok(couponService.existsActive(code));
    }
}