package com.hacisimsek.seller.kyc;

public class KycValidationException extends RuntimeException {
    public KycValidationException(String message) {
        super(message);
    }
}