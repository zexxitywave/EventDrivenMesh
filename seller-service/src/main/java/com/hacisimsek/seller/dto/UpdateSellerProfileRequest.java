package com.hacisimsek.seller.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateSellerProfileRequest {

    @Size(min = 2, max = 100, message = "Store name must be 2–100 characters")
    private String storeName;

    private String phone;

    private String businessAddress;
}
