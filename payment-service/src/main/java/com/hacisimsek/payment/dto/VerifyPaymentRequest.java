package com.hacisimsek.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Sent by the frontend after the user completes the payment in the gateway UI.
 * Used to verify the signature and capture the payment.
 */
@Data
public class VerifyPaymentRequest {

    @NotNull(message = "paymentId is required")
    private UUID paymentId;

    @NotBlank(message = "gatewayPaymentId is required")
    private String gatewayPaymentId;   // razorpay_payment_id / stripe payment intent id

    @NotBlank(message = "gatewayOrderId is required")
    private String gatewayOrderId;     // razorpay_order_id / stripe session id

    /** Razorpay HMAC signature — required for Razorpay, optional for Stripe. */
    private String gatewaySignature;
}
