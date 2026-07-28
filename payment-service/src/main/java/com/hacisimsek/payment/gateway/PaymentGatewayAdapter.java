package com.hacisimsek.payment.gateway;

import com.hacisimsek.payment.model.Payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Abstraction over any payment gateway.
 * Razorpay and Stripe provide their own implementations.
 * The service layer only depends on this interface.
 */
public interface PaymentGatewayAdapter {

    Payment.PaymentGateway getGateway();

    /**
     * Create an order/session on the gateway side.
     * Returns the gateway's order/session ID which the frontend uses to open the payment UI.
     */
    GatewayOrderResult createOrder(UUID internalPaymentId, BigDecimal amount, String currency);

    /**
     * Verify the payment callback from the frontend.
     * Returns true if the signature/status is valid and the payment was captured.
     */
    boolean verifyAndCapture(String gatewayPaymentId, String gatewayOrderId, String gatewaySignature);

    /**
     * Initiate a refund on the gateway.
     * Returns the gateway's refund ID for tracking.
     */
    String refund(String gatewayPaymentId, BigDecimal amount);

    /**
     * Verify the HMAC-SHA256 signature sent by Razorpay on every webhook POST.
     * The raw request body and the X-Razorpay-Signature header are passed in.
     * Returns true only if the computed digest matches the header value.
     * Default implementation returns false — override in gateway-specific adapters.
     */
    default boolean verifyWebhookSignature(String rawBody, String razorpaySignatureHeader) {
        return false;
    }
}
