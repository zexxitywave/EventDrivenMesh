package com.hacisimsek.common.event.inventory;

import com.hacisimsek.common.event.BaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Published by inventory-service after a saga compensation releases
 * reserved stock (payment failed, or shipping failed after capture).
 * order-service records this in the order event log as INVENTORY_RELEASED
 * so the trace shows the compensation step with truthful previousStatus.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public class StockReleasedEvent extends BaseEvent {
    private UUID orderId;
    private UUID productId;
    private Integer quantity;
    private UUID customerId;
    private String reason;

    public StockReleasedEvent(UUID correlationId, UUID orderId, UUID productId,
                               Integer quantity, UUID customerId, String reason) {
        super(correlationId);
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.customerId = customerId;
        this.reason = reason;
    }
}