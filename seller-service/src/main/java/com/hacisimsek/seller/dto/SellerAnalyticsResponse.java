package com.hacisimsek.seller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Aggregated sales analytics for a seller.
 * Data is compiled from order-service via REST.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerAnalyticsResponse {
    private UUID sellerId;
    private String storeName;
    private long totalOrders;
    private long pendingOrders;
    private long completedOrders;
    private long cancelledOrders;
    private BigDecimal totalRevenue;
    private int totalProductsListed;
    private BigDecimal averageOrderValue;
}
