package com.hacisimsek.common.event.shipping;

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
public class ShipmentProcessedEvent extends BaseEvent {
    private UUID orderId;
    private UUID shipmentId;
    private UUID customerId;
    private String customerEmail;
    private String trackingNumber;

    public ShipmentProcessedEvent(UUID correlationId, UUID orderId, UUID shipmentId,
                                   UUID customerId, String customerEmail, String trackingNumber) {
        super(correlationId);
        this.orderId = orderId;
        this.shipmentId = shipmentId;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.trackingNumber = trackingNumber;
    }
}
