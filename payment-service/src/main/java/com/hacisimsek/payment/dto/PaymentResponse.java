package com.hacisimsek.payment.dto;

import com.hacisimsek.payment.model.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private UUID id;
    private UUID orderId;
    private UUID customerId;
    private BigDecimal amount;
    private BigDecimal refundedAmount;
    private Payment.PaymentStatus status;
    private Payment.PaymentGateway gateway;
    private String paymentMethod;
    private String gatewayPaymentId;
    private String transactionId;
    private String failureReason;
    private Instant paymentDate;
    private Instant refundDate;
    private Instant createdAt;
}
