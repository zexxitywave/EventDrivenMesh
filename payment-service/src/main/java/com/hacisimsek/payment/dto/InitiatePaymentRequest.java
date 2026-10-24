package com.hacisimsek.payment.dto;

import com.hacisimsek.payment.model.Payment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sent by the frontend (or order-service) to initiate a payment.
 * Returns a gateway order/session that the frontend uses to open the payment UI.
 */
@Data
public class InitiatePaymentRequest {

    @NotNull(message = "orderId is required")
    private UUID orderId;

    @NotNull(message = "customerId is required")
    private UUID customerId;

    /** Customer email — carried through to PaymentProcessedEvent so notifications can send emails. */
    private String customerEmail;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    /** Which gateway to use — defaults to RAZORPAY if not provided. */
    private Payment.PaymentGateway gateway;

    private String paymentMethod;

    /**
     * Saga correlation ID — carried through PaymentProcessedEvent so the entire
     * order trace stays linked. Supplied by the client from the order response
     * (order-service persists it); also backfilled by preparePayment() if the
     * Kafka-driven saga message arrives first.
     */
    private UUID correlationId;
}
