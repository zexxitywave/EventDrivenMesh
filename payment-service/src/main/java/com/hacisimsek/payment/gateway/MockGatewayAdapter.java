package com.hacisimsek.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.hacisimsek.payment.model.Payment;

import lombok.extern.slf4j.Slf4j;

/**
 * Mock gateway — always succeeds by default.
 * Set payment.mock.force-failure=true to simulate a payment failure
 * and trigger the compensation path (inventory rollback, order CANCELLED).
 * Used by the saga auto-processing flow and local dev.
 */
@Component
@Slf4j
public class MockGatewayAdapter implements PaymentGatewayAdapter {

    @org.springframework.beans.factory.annotation.Value("${payment.mock.force-failure:false}")
    private boolean forceFailure;

    @Override
    public Payment.PaymentGateway getGateway() {
        return Payment.PaymentGateway.MOCK;
    }

    @Override
    public String getPublicKey() {
        return "mock_key";
    }

    @Override
    public GatewayOrderResult createOrder(UUID internalPaymentId, BigDecimal amount, String currency) {
        log.debug("[Mock] createOrder for payment={}", internalPaymentId);
        return new GatewayOrderResult("mock_order_" + internalPaymentId, "mock_key");
    }

    @Override
    public boolean verifyAndCapture(String gatewayPaymentId, String gatewayOrderId, String gatewaySignature) {
        if (forceFailure) {
            log.warn("[Mock] force-failure=true — simulating payment failure");
            return false;
        }
        log.debug("[Mock] verifyAndCapture — always returns true");
        return true;
    }

    @Override
    public String refund(String gatewayPaymentId, BigDecimal amount) {
        log.debug("[Mock] refund for payment={}, amount={}", gatewayPaymentId, amount);
        return "mock_refund_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
