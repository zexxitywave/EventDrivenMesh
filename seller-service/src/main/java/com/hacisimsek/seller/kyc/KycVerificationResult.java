package com.hacisimsek.seller.kyc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a KYC verification attempt against an external provider
 * (government / KYC aggregator).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycVerificationResult {
    private boolean verified;
    private String referenceId;
    private String rejectionReason;
}