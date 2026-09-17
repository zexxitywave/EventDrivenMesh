package com.hacisimsek.seller.model;

/**
 * KYC lifecycle for a seller.
 * NOT_SUBMITTED → PENDING → VERIFIED | REJECTED
 */
public enum KycStatus {
    NOT_SUBMITTED,
    PENDING,
    VERIFIED,
    REJECTED
}