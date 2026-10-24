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
public class ShipmentFailedEvent extends BaseEvent {
    private UUID orderId;
    private UUID customerId;
    private String customerEmail;
    private String reason;

    /** Legacy constructor — used by shipping-service (no customer info) */
    public ShipmentFailedEvent(UUID correlationId, UUID orderId, String reason) {
        super(correlationId);
        this.orderId = orderId;
        this.reason = reason;
    }

    /** Full constructor — preferred, includes customer info for notifications */
    public ShipmentFailedEvent(UUID correlationId, UUID orderId, UUID customerId,
                                String customerEmail, String reason) {
        super(correlationId);
        this.orderId = orderId;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.reason = reason;
    }
}
