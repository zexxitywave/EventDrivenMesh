package com.hacisimsek.seller.kyc;

/**
 * Abstraction of the external KYC verification authority.
 * Swap this for a real provider (Signzy, Karza, Digilocker, UIDAI) without
 * touching business logic.
 */
import java.time.LocalDate;

public interface KycVerificationProvider {

    String providerName();

    KycVerificationResult verify(String panNumber, String aadhaarNumber,
                                 String fullName, LocalDate dateOfBirth);
}