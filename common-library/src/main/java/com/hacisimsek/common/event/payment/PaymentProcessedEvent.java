package com.hacisimsek.common.event.payment;

import com.hacisimsek.common.event.BaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public class PaymentProcessedEvent extends BaseEvent {
    private UUID orderId;
    private UUID paymentId;
    private UUID customerId;
    private String customerEmail;
    private String shippingAddress;

    public PaymentProcessedEvent(UUID correlationId, UUID orderId, UUID paymentId,
                                  UUID customerId, String customerEmail, String shippingAddress) {
        super(correlationId);
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.shippingAddress = shippingAddress;
    }
}
