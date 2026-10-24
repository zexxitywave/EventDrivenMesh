package com.hacisimsek.wishlist.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private String productImageUrl;
    private String productSku;
    private Instant addedAt;
}
