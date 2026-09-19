package com.hacisimsek.common.event.order;

import com.hacisimsek.common.dto.OrderItemDto;
import com.hacisimsek.common.event.BaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public class OrderCreatedEvent extends BaseEvent {

    private UUID orderId;
    private UUID customerId;
    private String customerEmail;   // NEW

    private String shippingAddress; // real address captured at order time, flows to shipping-service

    private List<OrderItemDto> items;
    private BigDecimal totalAmount;
    private BigDecimal deliveryCharge;

    public OrderCreatedEvent(
            UUID correlationId,
            UUID orderId,
            UUID customerId,
            String customerEmail,
            String shippingAddress,
            List<OrderItemDto> items,
            BigDecimal totalAmount,
            BigDecimal deliveryCharge) {

        super(correlationId);
        this.orderId = orderId;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.shippingAddress = shippingAddress;
        this.items = items;
        this.totalAmount = totalAmount;
        this.deliveryCharge = deliveryCharge;
    }
}