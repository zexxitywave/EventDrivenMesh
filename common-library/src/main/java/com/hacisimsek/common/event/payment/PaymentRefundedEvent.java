package com.hacisimsek.common.event.payment;

import com.hacisimsek.common.event.BaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Published by payment-service after a saga compensation auto-refund
 * (e.g. shipping failed after payment was captured). order-service records
 * this in the order event log as PAYMENT_REFUNDED so the trace shows the
 * full compensation chain with truthful previousStatus.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public class PaymentRefundedEvent extends BaseEvent {
    private UUID orderId;
    private UUID paymentId;
    private UUID customerId;
    private String customerEmail;
    private String reason;

    public PaymentRefundedEvent(UUID correlationId, UUID orderId, UUID paymentId,
                                 UUID customerId, String customerEmail, String reason) {
        super(correlationId);
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.reason = reason;
    }
}