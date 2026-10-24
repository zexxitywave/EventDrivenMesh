package com.hacisimsek.payment.gateway;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Carries the gateway's order/session ID back to the service layer.
 */
@Data
@AllArgsConstructor
public class GatewayOrderResult {
    private String gatewayOrderId;
    private String publicKey;   // Razorpay key_id / Stripe publishable key
}
