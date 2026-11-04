package com.hacisimsek.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Wrapper returned by {@code GET /api/v1/cart/internal/abandoned}.
 *
 * <p>Kept as an object (not a bare array) so n8n receives one item with a
 * {@code carts} field that a Split Out node can expand deterministically.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbandonedCartsResponse {

    private int count;
    private List<AbandonedCartResponse> carts;
}
