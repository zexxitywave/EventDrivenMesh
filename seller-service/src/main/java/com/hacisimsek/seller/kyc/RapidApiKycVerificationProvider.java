package com.hacisimsek.seller.kyc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Real KYC verification against IDfy's Indian PAN verification API,
 * proxied through the RapidAPI marketplace.
 *
 *   POST https://idfy-verification-suite.p.rapidapi.com/v3/tasks/sync/verify_with_source/ind_pan
 *   headers: Content-Type: application/json
 *            x-rapidapi-host: <host>
 *            x-rapidapi-key:  <api key>
 *   body:    { "task_id": "<uuid>", "group_id": "<uuid>",
 *              "data": { "id_number": "ABCDE1234F", "full_name": "ABC XYZ", "dob": "1990-01-01" } }
 *
 * The PAN is verified against the Income Tax registry. A response status of
 * "id_found" (source_output.status) classifies as verified; "id_not_found"
 * is a hard rejection (PAN doesn't exist or name/DOB don't match the record).
 * Infrastructure errors surface as a retriable KycValidationException
 * (never silently stored as REJECTED).
 *
 * API docs: https://eve-api-docs.idfy.com  (Postman collection, endpoint: verify_with_source/ind_pan)
 */
@Component
@Slf4j
public class RapidApiKycVerificationProvider implements KycVerificationProvider {

    private static final String VERIFY_PATH = "/v3/tasks/sync/verify_with_source/ind_pan";
    private static final String ID_FOUND = "id_found";
    private static final DateTimeFormatter DOB_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final RapidApiProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public RapidApiKycVerificationProvider(RapidApiProperties properties,
                                           RestTemplate restTemplate,
                                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        return "RAPIDAPI-IDFY-PAN";
    }

    @Override
    public KycVerificationResult verify(String panNumber, String aadhaarNumber,
                                        String fullName, LocalDate dateOfBirth) {
        ensureConfigured();

        String url = "https://" + properties.getHost() + VERIFY_PATH;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-rapidapi-host", properties.getHost());
        headers.set("x-rapidapi-key", properties.getApiKey());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_number", panNumber);
        data.put("full_name", fullName);
        data.put("dob", dateOfBirth.format(DOB_FORMATTER));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("task_id", UUID.randomUUID().toString());
        body.put("group_id", UUID.randomUUID().toString());
        body.put("data", data);

        try {
            JsonNode root = restTemplate.postForObject(
                    url, new HttpEntity<>(body, headers), JsonNode.class);

            JsonNode sourceOutput = root == null
                    ? null
                    : root.path("result").path("source_output");
            String status = sourceOutput == null ? "" : sourceOutput.path("status").asText("");
            String panStatus = sourceOutput == null ? "" : sourceOutput.path("pan_status").asText("");
            String requestId = root == null ? null : root.path("request_id").asText(null);
            boolean nameMatch = sourceOutput == null || sourceOutput.path("name_match").asBoolean(true);
            boolean dobMatch = sourceOutput == null || sourceOutput.path("dob_match").asBoolean(true);

            if (ID_FOUND.equalsIgnoreCase(status) && nameMatch && dobMatch) {
                log.info("[{}] PAN {} (masked) verified via Income Tax registry, requestId={}",
                        providerName(), maskPan(panNumber), requestId);
                return KycVerificationResult.builder()
                        .verified(true)
                        .referenceId(requestId)
                        .build();
            }

            log.warn("[{}] PAN {} (masked) NOT verified by registry (status={}, nameMatch={}, dobMatch={}), requestId={}",
                    providerName(), maskPan(panNumber), status, nameMatch, dobMatch, requestId);
            return KycVerificationResult.builder()
                    .verified(false)
                    .rejectionReason("PAN not validated by the Income Tax registry (status='"
                            + status + "'"
                            + (panStatus.isBlank() ? "" : ", " + panStatus)
                            + ", nameMatch=" + nameMatch + ", dobMatch=" + dobMatch
                            + ") — no active PAN record matching the submitted name/DOB")
                    .build();
        } catch (HttpClientErrorException e) {
            throw new KycValidationException("KYC provider rejected the request: "
                    + extractDetail(e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            throw new KycValidationException("KYC provider unreachable: " + e.getMessage());
        } catch (Exception e) {
            throw new KycValidationException("Failed to parse KYC provider response: " + e.getMessage());
        }
    }

    private void ensureConfigured() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new KycValidationException(
                    "RapidAPI key not configured. Sign up free at https://rapidapi.com "
                    + "(Dashboard → Apps → copy X-RapidAPI-Key) and set env var RAPIDAPI_KYC_KEY.");
        }
    }

    private String extractDetail(String body) {
        if (body == null || body.isBlank()) {
            return "no error details returned";
        }
        try {
            JsonNode detail = objectMapper.readTree(body).path("detail");
            return detail.isTextual() ? detail.asText() : body;
        } catch (Exception e) {
            return body;
        }
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() != 10) return "****";
        return pan.substring(0, 5) + "****" + pan.substring(9);
    }
}