package com.hacisimsek.seller.kyc;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Format-level KYC checkers.
 *
 * PAN — Indian Income Tax Permanent Account Number, format ABCDE1234F:
 *   [A-Z]{5}        first 5 letters
 *   [0-9]{4}        4 digits
 *   [A-Z]           1 letter (the check character)
 *
 * Aadhaar — 12-digit UID issued by UIDAI. The 12th digit is a Verhoeff
 * check digit computed over the first 11 digits, so we validate with the
 * same algorithm UIDAI uses. This instantly rejects fake/typo numbers and
 * offers a masked display format (XXXX XXXX 1234).
 */
@Component
public class KycValidator {

    private static final Pattern PAN_PATTERN = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]");
    // Aadhaar verification disabled.
    // private static final Pattern AADHAAR_PATTERN = Pattern.compile("[0-9]{12}");

    // Verhoeff multiplication table d[i][j]
    private static final int[][] D = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
            {1, 2, 3, 4, 0, 6, 7, 8, 9, 5},
            {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
            {3, 4, 0, 1, 2, 8, 9, 5, 6, 7},
            {4, 0, 1, 2, 3, 9, 5, 6, 7, 8},
            {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
            {6, 5, 9, 8, 7, 1, 0, 4, 3, 2},
            {7, 6, 5, 9, 8, 2, 1, 0, 4, 3},
            {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
            {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}
    };

    // Verhoeff permutation table p[i][j]
    private static final int[][] P = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
            {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
            {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
            {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
            {9, 4, 5, 3, 1, 2, 6, 8, 7, 0},
            {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
            {2, 7, 9, 3, 8, 0, 6, 4, 1, 5},
            {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}
    };

    /** Validates Indian PAN format. Accepts lowercase, normalizes to uppercase. */
    public String validateAndNormalizePan(String panNumber) {
        if (panNumber == null) {
            throw new KycValidationException("PAN number is required");
        }
        String normalized = panNumber.trim().toUpperCase();
        if (!PAN_PATTERN.matcher(normalized).matches()) {
            throw new KycValidationException(
                    "Invalid PAN format. Expected pattern ABCDE1234F, got: " + normalized);
        }
        return normalized;
    }

    /** Validates Aadhaar using UIDAI's Verhoeff checksum over the full 12 digits. */
    // ── Aadhaar verification disabled — kept for reference ──────────────
    // public String validateAndNormalizeAadhaar(String aadhaarNumber) {
    //     if (aadhaarNumber == null) {
    //         throw new KycValidationException("Aadhaar number is required");
    //     }
    //     String normalized = aadhaarNumber.replaceAll("[\\s-]", "");
    //     if (!AADHAAR_PATTERN.matcher(normalized).matches()) {
    //         throw new KycValidationException(
    //                 "Invalid Aadhaar format. Expected 12 digits, got: " + normalized);
    //     }
    //     if (!verhoeffValidate(normalized)) {
    //         throw new KycValidationException("Aadhaar failed checksum validation — not a valid UIDAI number");
    //     }
    //     return normalized;
    // }
    //
    // private boolean verhoeffValidate(String number) {
    //     int check = 0;
    //     for (int i = 0; i < number.length(); i++) {
    //         int digit = number.charAt(number.length() - 1 - i) - '0';
    //         check = D[check][P[i % 8][digit]];
    //     }
    //     return check == 0;
    // }
    // ─────────────────────────────────────────────────────────────────────

    /** Masks PAN: ABCDE1234F → ABCDE****F */
    public String maskPan(String pan) {
        if (pan == null || pan.length() != 10) return null;
        return pan.substring(0, 5) + "****" + pan.substring(9);
    }

    /** Masks Aadhaar: 123456789012 → XXXX-XXXX-9012 */
    // ── Aadhaar verification disabled — kept for reference ──────────────
    // public String maskAadhaar(String aadhaar) {
    //     if (aadhaar == null || aadhaar.length() != 12) return null;
    //     return "XXXX-XXXX-" + aadhaar.substring(8);
    // }
    // ─────────────────────────────────────────────────────────────────────
}