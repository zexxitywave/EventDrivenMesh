package com.hacisimsek.seller.kyc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the real IDfy PAN verification API, accessed through
 * the RapidAPI marketplace (personal-email signup is fine).
 *
 *   https://rapidapi.com/idfy-idfy-default/api/pan-card-verification1   (free tier: 50 req/month)
 *
 * Sign up → Dashboard → Apps → copy your default X-RapidAPI-Key.
 * Override via env vars so secrets never live in the repo:
 *
 *   RAPIDAPI_KYC_HOST    (default idfy-verification-suite.p.rapidapi.com)
 *   RAPIDAPI_KYC_KEY
 */
@Data
@ConfigurationProperties(prefix = "rapidapi.kyc")
public class RapidApiProperties {

    private String host = "idfy-verification-suite.p.rapidapi.com";

    private String apiKey;
}