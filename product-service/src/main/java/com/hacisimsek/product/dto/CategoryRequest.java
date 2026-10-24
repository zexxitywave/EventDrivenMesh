package com.hacisimsek.product.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CategoryRequest {
    @NotBlank(message = "Category name is required")
    private String name;

    private String description;

    private String slug;

    /** Optional parent category ID for nested categories */
    private UUID parentId;
}
