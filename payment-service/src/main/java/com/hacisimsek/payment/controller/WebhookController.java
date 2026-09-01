package com.hacisimsek.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.payment.gateway.PaymentGatewayAdapter;
import com.hacisimsek.payment.model.Payment;
import com.hacisimsek.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Receives webhook events pushed by Razorpay to your server.
 *
 * Razorpay Dashboard â†’ Settings â†’ Webhooks â†’ Add new webhook:
 *   URL:    https://<your-domain>/api/payments/webhook/razorpay
 *   Events: payment.captured, payment.failed, refund.created
 *   Secret: value of RAZORPAY_WEBHOOK_SECRET env var
 *
 * IMPORTANT: This endpoint is intentionally excluded from JWT auth in the
 * API Gateway / Security config â€” Razorpay calls it directly, not the user.
 * Security is provided solely by HMAC-SHA256 signature verification.
 *
 * Spring must receive the raw bytes (not a parsed object) so the signature
 * is verified against the exact body Razorpay signed. We read it as byte[]
 * and convert to String only after verification passes.
 */
@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    /** All gateway adapters â€” used to look up the Razorpay adapter by type. */
    private final List<PaymentGatewayAdapter> gatewayAdapters;

    /**
     * POST /api/payments/webhook/razorpay
     *
     * Headers sent by Razorpay:
     *   Content-Type:        application/json
     *   X-Razorpay-Signature: <HMAC-SHA256 hex digest>
     *
     * Response contract:
     *   200 OK   â†’ event acknowledged (Razorpay will not retry)
     *   400      â†’ signature invalid  (logged, no retry by Razorpay for bad sig)
     *   500      â†’ processing error   (Razorpay WILL retry â€” safe to throw on transient errors)
     */
    @PostMapping(
            value = "/razorpay",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, String>> handleRazorpayWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        String bodyString = new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);

        log.info("[Webhook] Razorpay event received, bodyLength={}, signaturePresent={}",
                rawBody.length, signature != null);

        // â”€â”€ 1. Verify HMAC signature â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        PaymentGatewayAdapter razorpayAdapter = resolveAdapter(Payment.PaymentGateway.RAZORPAY);
        boolean signatureValid = razorpayAdapter.verifyWebhookSignature(bodyString, signature);

        if (!signatureValid) {
            log.error("[Webhook] Razorpay signature verification FAILED â€” rejecting event");
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", "Invalid signature"));
        }

        // â”€â”€ 2. Parse event type â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        String eventType;
        try {
            JsonNode root = objectMapper.readTree(bodyString);
            eventType = root.path("event").asText();
            if (eventType.isBlank()) {
                log.warn("[Webhook] No 'event' field in payload â€” ignoring");
                return ResponseEntity.ok(Map.of("status", "ignored", "reason", "missing event field"));
            }
        } catch (Exception e) {
            log.error("[Webhook] Failed to parse webhook JSON: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", "Invalid JSON payload"));
        }

        // â”€â”€ 3. Delegate to service â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        try {
            paymentService.handleWebhookEvent(eventType, bodyString);
            log.info("[Webhook] Event '{}' processed successfully", eventType);
            return ResponseEntity.ok(Map.of("status", "ok", "event", eventType));
        } catch (Exception e) {
            // Return 500 so Razorpay retries on transient failures (DB down, Kafka unavailable)
            log.error("[Webhook] Error processing event '{}': {}", eventType, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "Processing failed â€” will retry"));
        }
    }

    // â”€â”€ Helper â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private PaymentGatewayAdapter resolveAdapter(Payment.PaymentGateway gateway) {
        Map<Payment.PaymentGateway, PaymentGatewayAdapter> index = gatewayAdapters.stream()
                .collect(Collectors.toMap(PaymentGatewayAdapter::getGateway, Function.identity()));
        PaymentGatewayAdapter adapter = index.get(gateway);
        if (adapter == null) {
            throw new IllegalStateException("No gateway adapter found for: " + gateway);
        }
        return adapter;
    }
}
