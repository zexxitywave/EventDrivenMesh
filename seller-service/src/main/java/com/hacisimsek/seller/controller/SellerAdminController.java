package com.hacisimsek.seller.controller;

import com.hacisimsek.seller.dto.SellerProfileResponse;
import com.hacisimsek.seller.model.VerificationStatus;
import com.hacisimsek.seller.service.SellerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only endpoints for seller verification workflow.
 * In production these should be protected by a ROLE_ADMIN check
 * in the API Gateway or a Spring Security filter.
 */
@RestController
@RequestMapping("/api/v1/admin/sellers")
@RequiredArgsConstructor
public class SellerAdminController {

    private final SellerService sellerService;

    /** Update seller verification status â€” VERIFIED, REJECTED, SUSPENDED, etc. */
    @PatchMapping("/{sellerId}/verify")
    public ResponseEntity<SellerProfileResponse> updateVerification(
            @PathVariable UUID sellerId,
            @RequestParam VerificationStatus status,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(sellerService.updateVerificationStatus(sellerId, status, notes));
    }
}
