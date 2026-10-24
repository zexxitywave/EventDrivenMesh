package com.hacisimsek.seller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Minimal order projection returned by order-service for seller's order list.
 */
@Data
public class OrderSummary {
    private UUID orderId;
    private UUID customerId;
    private BigDecimal totalAmount;
    private String status;
    private Instant createdAt;
}
