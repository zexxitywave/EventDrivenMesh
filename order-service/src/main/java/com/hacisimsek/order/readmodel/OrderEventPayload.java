package com.hacisimsek.order.readmodel;

import com.hacisimsek.common.dto.OrderItemDto;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Generic view of any saga event projected into the read model.
 *
 * <p>All events published on order-events / inventory-events / payment-events /
 * shipping-events / compensation-events share at least orderId, customerId and
 * customerEmail; only OrderCreatedEvent carries items and totalAmount. Jackson
 * simply ignores the extra fields (the Spring Boot ObjectMapper is configured
 * with FAIL_ON_UNKNOWN_PROPERTIES=false), so one DTO covers every event type.
 */
@Data
public class OrderEventPayload {

    private UUID orderId;
    private UUID customerId;
    private String customerEmail;
    private UUID correlationId;
    private BigDecimal totalAmount;
    private List<OrderItemDto> items;
    private Instant timestamp;
}