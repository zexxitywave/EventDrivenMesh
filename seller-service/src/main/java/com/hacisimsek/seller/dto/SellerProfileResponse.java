package com.hacisimsek.seller.dto;

import com.hacisimsek.seller.model.KycStatus;
import com.hacisimsek.seller.model.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerProfileResponse {
    private UUID sellerId;
    private UUID userId;
    private String storeName;
    private String email;
    private String phone;
    private String businessAddress;
    private VerificationStatus verificationStatus;
    private KycStatus kycStatus;
    private String panMasked;
    // private String aadhaarMasked;
    private BigDecimal rating;
    private Integer ratingCount;
    private Instant createdAt;
    private Instant updatedAt;
}
