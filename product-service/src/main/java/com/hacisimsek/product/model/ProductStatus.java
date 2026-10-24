package com.hacisimsek.product.model;

public enum ProductStatus {
    ACTIVE,       // visible and purchasable
    INACTIVE,     // hidden from catalog
    OUT_OF_STOCK, // visible but not purchasable
    DISCONTINUED  // permanently removed from catalog
}
