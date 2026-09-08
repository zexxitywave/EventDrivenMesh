package com.hacisimsek.payment.gateway;

import com.hacisimsek.payment.model.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stripe gateway adapter.
 *
 * In production, add the Stripe Java SDK and call Stripe.apiKey + PaymentIntent.create()
 *
 * Dependency to add:
 *   <dependency>
 *     <groupId>com.stripe</groupId>
 *     <artifactId>stripe-java</artifactId>
 *     <version>25.3.0</version>
 *   </dependency>
 */
@Component
@Slf4j
public class StripeGatewayAdapter implements PaymentGatewayAdapter {

    @Value("${payment.stripe.secret-key:sk_test_placeholder}")
    private String secretKey;

    @Value("${payment.stripe.publishable-key:pk_test_placeholder}")
    private String publishableKey;

    @Override
    public Payment.PaymentGateway getGateway() {
        return Payment.PaymentGateway.STRIPE;
    }

    @Override
    public String getPublicKey() {
        return publishableKey;
    }

    @Override
    public GatewayOrderResult createOrder(UUID internalPaymentId, BigDecimal amount, String currency) {
        log.info("[Stripe] Creating PaymentIntent for payment={}, amount={} {}", internalPaymentId, amount, currency);

        /*
         * Live implementation:
         * Stripe.apiKey = secretKey;
         * PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
         *     .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
         *     .setCurrency(currency.toLowerCase())
         *     .setMetadata(Map.of("internalPaymentId", internalPaymentId.toString()))
         *     .build();
         * PaymentIntent intent = PaymentIntent.create(params);
         * return new GatewayOrderResult(intent.getClientSecret(), publishableKey);
         */

        String fakeIntentId = "pi_" + internalPaymentId.toString().replace("-", "").substring(0, 20);
        log.warn("[Stripe] Using STUB gateway — replace with real SDK call before production");
        return new GatewayOrderResult(fakeIntentId, publishableKey);
    }

    @Override
    public boolean verifyAndCapture(String gatewayPaymentId, String gatewayOrderId, String gatewaySignature) {
        log.info("[Stripe] Verifying PaymentIntent={}", gatewayPaymentId);

        /*
         * Stripe verification — retrieve the PaymentIntent and check status == "succeeded":
         * Stripe.apiKey = secretKey;
         * PaymentIntent intent = PaymentIntent.retrieve(gatewayPaymentId);
         * return "succeeded".equals(intent.getStatus());
         */

        log.warn("[Stripe] Using STUB verification — replace with real SDK call before production");
        return true;
    }

    @Override
    public String refund(String gatewayPaymentId, BigDecimal amount) {
        log.info("[Stripe] Initiating refund for PaymentIntent={}, amount={}", gatewayPaymentId, amount);

        /*
         * Live implementation:
         * Stripe.apiKey = secretKey;
         * RefundCreateParams params = RefundCreateParams.builder()
         *     .setPaymentIntent(gatewayPaymentId)
         *     .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
         *     .build();
         * com.stripe.model.Refund refund = com.stripe.model.Refund.create(params);
         * return refund.getId();
         */

        String fakeRefundId = "re_" + gatewayPaymentId.substring(Math.max(0, gatewayPaymentId.length() - 10));
        log.warn("[Stripe] Using STUB refund — replace with real SDK call before production");
        return fakeRefundId;
    }
}
