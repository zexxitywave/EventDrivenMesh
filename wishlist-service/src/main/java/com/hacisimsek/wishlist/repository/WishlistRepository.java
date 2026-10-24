package com.hacisimsek.wishlist.repository;

import com.hacisimsek.wishlist.model.WishlistItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WishlistRepository extends MongoRepository<WishlistItem, UUID> {

    List<WishlistItem> findByUserId(UUID userId);

    Optional<WishlistItem> findByUserIdAndProductId(UUID userId, UUID productId);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    void deleteByUserIdAndProductId(UUID userId, UUID productId);

    long countByUserId(UUID userId);
}
