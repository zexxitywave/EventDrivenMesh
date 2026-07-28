package com.hacisimsek.payment.service;

import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.payment.dto.GatewayOrderResponse;
import com.hacisimsek.payment.dto.InitiatePaymentRequest;
import com.hacisimsek.payment.dto.PaymentResponse;
import com.hacisimsek.payment.dto.RefundRequest;
import com.hacisimsek.payment.dto.VerifyPaymentRequest;
import com.hacisimsek.payment.model.Payment;

import java.util.List;
import java.util.UUID;

public interface PaymentService {

    /** Called by saga — auto-processes payment for saga-driven orders. */
    void processPayment(InventoryReservedEvent event);

    /** Initiate a payment session — returns gateway order/session for frontend. */
    GatewayOrderResponse initiatePayment(InitiatePaymentRequest request);

    /** Verify and capture — called after user completes gateway UI. */
    PaymentResponse verifyPayment(VerifyPaymentRequest request);

    /** Initiate full or partial refund. */
    PaymentResponse refundPayment(RefundRequest request);

    /** Get payment by internal payment ID. */
    PaymentResponse getPaymentById(UUID paymentId);

    /** Get payment by order ID. */
    PaymentResponse getPaymentByOrderId(UUID orderId);

    /** Get all payments for a customer. */
    List<PaymentResponse> getPaymentsByCustomerId(UUID customerId);

    /**
     * Handle a verified Razorpay webhook event payload.
     * Parses the event type and routes to the correct handler:
     *   payment.captured  → mark COMPLETED, publish PaymentProcessedEvent
     *   payment.failed    → mark FAILED,     publish PaymentFailedEvent
     *   refund.created    → mark REFUNDED / PARTIALLY_REFUNDED
     */
    void handleWebhookEvent(String eventType, String webhookPayload);
}
