package com.hacisimsek.wishlist.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AddToWishlistRequest {

    @NotNull(message = "productId is required")
    private UUID productId;
}
