package com.hacisimsek.wishlist.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * One document per (userId, productId) pair.
 * The compound unique index enforces the "no duplicates" constraint at the DB level.
 */
@Document(collection = "wishlist_items")
@CompoundIndexes({
    @CompoundIndex(name = "user_product_unique", def = "{'userId': 1, 'productId': 1}", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItem {

    @Id
    private UUID id;

    @Indexed
    private UUID userId;

    private UUID productId;

    /** Denormalized snapshot from product-service — avoids a join on every wishlist read. */
    private String productName;
    private String productImageUrl;
    private String productSku;

    @Builder.Default
    private Instant addedAt = Instant.now();
}
