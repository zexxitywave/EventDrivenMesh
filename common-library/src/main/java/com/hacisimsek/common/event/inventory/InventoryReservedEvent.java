package com.hacisimsek.common.event.inventory;

import com.hacisimsek.common.event.BaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public class InventoryReservedEvent extends BaseEvent {
    private UUID orderId;
    private UUID customerId;
    private String customerEmail;
    private String shippingAddress;
    private BigDecimal totalAmount;

    public InventoryReservedEvent(UUID correlationId, UUID orderId, UUID customerId,
                                   String customerEmail, String shippingAddress, BigDecimal totalAmount) {
        super(correlationId);
        this.orderId = orderId;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.shippingAddress = shippingAddress;
        this.totalAmount = totalAmount;
    }
}
