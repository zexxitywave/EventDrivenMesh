package com.hacisimsek.seller.dto;

import com.hacisimsek.seller.model.KycStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * KYC status response. PAN and Aadhaar are ALWAYS masked — full values are never exposed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycStatusResponse {
    private KycStatus kycStatus;
    private String panMasked;
    // private String aadhaarMasked;
    private String kycReferenceId;
    private String rejectionReason;
    private Instant kycVerifiedAt;
}