package com.hacisimsek.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Result of {@code GET /api/v1/orders/customer/{customerId}/purchased}.
 *
 * <p>Answers one business question for the n8n abandoned-cart workflow:
 * "since this cart was last touched, did the customer place an order that
 * contains any of these products?"</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseCheckResponse {

    private UUID customerId;
    private boolean purchased;
    private UUID matchedOrderId;
    private List<UUID> matchedProductIds;
    private Instant since;
    private int ordersChecked;
}
