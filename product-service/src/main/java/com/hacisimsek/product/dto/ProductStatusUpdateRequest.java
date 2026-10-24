package com.hacisimsek.product.dto;

import com.hacisimsek.product.model.ProductStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductStatusUpdateRequest {
    @NotNull(message = "Status is required")
    private ProductStatus status;
}
