package com.hacisimsek.seller.service;

import com.hacisimsek.seller.dto.KycStatusResponse;
import com.hacisimsek.seller.dto.KycSubmissionRequest;
import com.hacisimsek.seller.kyc.KycValidationException;
import com.hacisimsek.seller.kyc.KycValidator;
import com.hacisimsek.seller.kyc.KycVerificationProvider;
import com.hacisimsek.seller.kyc.KycVerificationResult;
import com.hacisimsek.seller.model.KycStatus;
import com.hacisimsek.seller.model.SellerProfile;
import com.hacisimsek.seller.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycService {

    private final SellerRepository sellerRepository;
    private final KycValidator kycValidator;
    private final KycVerificationProvider kycVerificationProvider;

    @Transactional
    public KycStatusResponse submitKyc(UUID userId, KycSubmissionRequest request) {
        SellerProfile seller = findByUserId(userId);

        if (seller.getKycStatus() == KycStatus.VERIFIED) {
            throw new IllegalStateException("KYC is already verified for this seller");
        }

        String pan = kycValidator.validateAndNormalizePan(request.getPanNumber());
        // Aadhaar verification disabled — see KycSubmissionRequest.
        // String aadhaar = kycValidator.validateAndNormalizeAadhaar(request.getAadhaarNumber());

        if (sellerRepository.existsByPanNumber(pan)) {
            throw new KycValidationException("PAN is already registered to another seller");
        }
        // Aadhaar uniqueness check disabled while Aadhaar verification is off.
        // if (sellerRepository.existsByAadhaarNumber(aadhaar)) {
        //     throw new KycValidationException("Aadhaar is already registered to another seller");
        // }

        KycVerificationResult result = kycVerificationProvider.verify(
                pan, request.getFullName(), request.getDateOfBirth());

        if (result.isVerified()) {
            seller.setPanNumber(pan);
            // seller.setAadhaarNumber(aadhaar);
            seller.setKycStatus(KycStatus.VERIFIED);
            seller.setKycReferenceId(result.getReferenceId());
            seller.setKycVerifiedAt(Instant.now());
            seller.setKycRejectionReason(null);
            log.info("Seller {} KYC VERIFIED (ref={})", seller.getSellerId(), result.getReferenceId());
        } else {
            seller.setKycStatus(KycStatus.REJECTED);
            seller.setKycRejectionReason(result.getRejectionReason());
            log.warn("Seller {} KYC REJECTED: {}", seller.getSellerId(), result.getRejectionReason());
        }

        return toResponse(sellerRepository.save(seller));
    }

    @Transactional(readOnly = true)
    public KycStatusResponse getKycStatus(UUID userId) {
        return toResponse(findByUserId(userId));
    }

    private SellerProfile findByUserId(UUID userId) {
        return sellerRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Seller profile not found for user: " + userId));
    }

    private KycStatusResponse toResponse(SellerProfile seller) {
        return KycStatusResponse.builder()
                .kycStatus(seller.getKycStatus())
                .panMasked(kycValidator.maskPan(seller.getPanNumber()))
                // .aadhaarMasked(kycValidator.maskAadhaar(seller.getAadhaarNumber()))
                .kycReferenceId(seller.getKycReferenceId())
                .rejectionReason(seller.getKycRejectionReason())
                .kycVerifiedAt(seller.getKycVerifiedAt())
                .build();
    }
}