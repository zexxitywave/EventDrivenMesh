package com.hacisimsek.order.readmodel;

import com.hacisimsek.order.dto.OrderItemResponse;
import com.hacisimsek.order.model.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response served by the CQRS read endpoints. Mirrors the shape of
 * {@code OrderResponse} plus the projection metadata, so a consumer can tell
 * (and verify) that the read came from the read model and which event produced
 * the latest state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReadResponse {

    public static final String SOURCE_READ_MODEL = "READ_MODEL";

    private UUID orderId;
    private UUID customerId;
    private String customerEmail;
    private BigDecimal totalAmount;
    private Order.OrderStatus status;
    private UUID correlationId;
    private Integer itemCount;
    private List<OrderItemResponse> items;
    private boolean refunded;
    private boolean stockReleased;
    private Instant createdAt;
    private Instant updatedAt;

    // Projection metadata
    private String source;
    private String lastEvent;
    private Instant lastEventAt;
}