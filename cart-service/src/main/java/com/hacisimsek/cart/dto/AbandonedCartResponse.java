package com.hacisimsek.cart.dto;

import com.hacisimsek.cart.model.CartItem;
import com.hacisimsek.cart.model.ReminderStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One row returned by {@code GET /api/v1/cart/internal/abandoned}.
 * Consumed by the n8n abandoned-cart recovery workflow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbandonedCartResponse {

    private UUID cartId;
    private UUID userId;
    private List<CartItem> items;
    private int itemCount;
    private BigDecimal grandTotal;
    private Instant createdAt;
    private Instant updatedAt;
    private long minutesIdle;
    private ReminderStage reminderStage;
    private Instant lastReminderAt;
}
