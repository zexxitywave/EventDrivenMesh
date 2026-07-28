package com.hacisimsek.payment.gateway;

import com.hacisimsek.payment.model.Payment;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

@Component
@Slf4j
public class RazorpayGatewayAdapter implements PaymentGatewayAdapter {

    @Value("${payment.razorpay.key-id}")
    private String keyId;

    @Value("${payment.razorpay.key-secret}")
    private String keySecret;

    @Value("${payment.razorpay.webhook-secret}")
    private String webhookSecret;

    @Override
    public Payment.PaymentGateway getGateway() {
        return Payment.PaymentGateway.RAZORPAY;
    }

    @Override
    public GatewayOrderResult createOrder(UUID internalPaymentId, BigDecimal amount, String currency) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            // Razorpay expects amount in smallest currency unit (paise for INR)
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount.multiply(BigDecimal.valueOf(100)).intValue());
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", internalPaymentId.toString());

            Order order = client.orders.create(orderRequest);
            String gatewayOrderId = order.get("id");

            log.info("[Razorpay] Created order: {}", gatewayOrderId);

            return new GatewayOrderResult(gatewayOrderId, keyId);

        } catch (RazorpayException e) {
            log.error("[Razorpay] Order creation failed: {}", e.getMessage());
            throw new RuntimeException("Failed to create Razorpay order: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyAndCapture(String gatewayPaymentId, String gatewayOrderId, String gatewaySignature) {
        log.info("[Razorpay] Verifying payment={}, order={}", gatewayPaymentId, gatewayOrderId);

        try {
            if (gatewaySignature == null || gatewaySignature.isBlank()) {
                log.warn("[Razorpay] No signature provided — skipping verification (dev mode)");
                return true;
            }

            // HMAC-SHA256: signature = HMAC(gatewayOrderId + "|" + gatewayPaymentId, keySecret)
            String payload = gatewayOrderId + "|" + gatewayPaymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);

            boolean valid = computed.equals(gatewaySignature);
            if (!valid) {
                log.error("[Razorpay] Signature mismatch — possible tampered request");
            }
            return valid;

        } catch (Exception e) {
            log.error("[Razorpay] Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String refund(String gatewayPaymentId, BigDecimal amount) {
        log.info("[Razorpay] Initiating refund for payment={}, amount={}", gatewayPaymentId, amount);

        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", amount.multiply(BigDecimal.valueOf(100)).intValue());

            com.razorpay.Refund refund = client.payments.refund(gatewayPaymentId, refundRequest);
            String refundId = refund.get("id");

            log.info("[Razorpay] Refund created: {}", refundId);
            return refundId;

        } catch (RazorpayException e) {
            log.error("[Razorpay] Refund failed: {}", e.getMessage());
            throw new RuntimeException("Failed to process Razorpay refund: " + e.getMessage(), e);
        }
    }

    /**
     * Razorpay sends a SHA-256 HMAC of the raw request body signed with the
     * webhook secret (different from key-secret). The header is X-Razorpay-Signature.
     *
     * Docs: https://razorpay.com/docs/webhooks/validate-test/
     */
    @Override
    public boolean verifyWebhookSignature(String rawBody, String razorpaySignatureHeader) {
        if (razorpaySignatureHeader == null || razorpaySignatureHeader.isBlank()) {
            log.warn("[Razorpay] Webhook received with no signature header — rejecting");
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);

            boolean valid = computed.equals(razorpaySignatureHeader);
            if (valid) {
                log.info("[Razorpay] Webhook signature verified successfully");
            } else {
                log.error("[Razorpay] Webhook signature mismatch — possible forged request");
            }
            return valid;

        } catch (Exception e) {
            log.error("[Razorpay] Webhook signature verification error: {}", e.getMessage());
            return false;
        }
    }
}
