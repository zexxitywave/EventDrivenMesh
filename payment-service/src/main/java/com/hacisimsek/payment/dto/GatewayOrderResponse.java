package com.hacisimsek.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Returned to the client after initiating a payment.
 * Contains gateway-specific data the frontend needs to open the payment UI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayOrderResponse {
    private UUID paymentId;       // internal payment record ID
    private String gatewayOrderId; // Razorpay order_id / Stripe payment_intent id
    private String gatewayKey;     // Razorpay key_id (public) — safe to expose to client
    private BigDecimal amount;
    private String currency;
    private String status;
}
