package com.hacisimsek.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private UUID customerId;

    private UUID correlationId;

    private String customerEmail;

    /** Delivery address captured at order time — relayed to shipping-service. */
    @Column(length = 500)
    private String shippingAddress;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Actual amount refunded — null until a refund is processed. */
    @Column(precision = 12, scale = 2)
    private BigDecimal refundedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    /**
     * Gateway used: RAZORPAY or STRIPE.
     * Determines which gateway adapter is invoked.
     */
    @Enumerated(EnumType.STRING)
    private PaymentGateway gateway;

    /** Payment method provided by user: CARD, UPI, NET_BANKING, WALLET. */
    private String paymentMethod;

    /** Gateway's own transaction/payment ID — used for refunds and reconciliation. */
    private String gatewayPaymentId;

    /** Gateway's order ID (Razorpay creates an order before capturing). */
    private String gatewayOrderId;

    /** Razorpay signature or Stripe payment intent ID for verification. */
    private String gatewaySignature;

    /** Failure reason from gateway — populated on FAILED status. */
    @Column(length = 500)
    private String failureReason;

    /** Internal transaction ID generated after successful capture. */
    private String transactionId;

    private Instant paymentDate;
    private Instant refundDate;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public enum PaymentStatus {
        PENDING,        // awaiting gateway callback
        AUTHORIZED,     // captured/authorized by gateway
        COMPLETED,      // fully confirmed and settled
        FAILED,         // gateway rejected
        REFUND_PENDING, // refund request submitted to gateway
        REFUNDED,       // fully refunded
        PARTIALLY_REFUNDED
    }

    public enum PaymentGateway {
        RAZORPAY,
        STRIPE,
        MOCK   // for local dev/testing
    }
}
