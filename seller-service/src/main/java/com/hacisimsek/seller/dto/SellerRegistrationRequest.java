package com.hacisimsek.seller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SellerRegistrationRequest {

    @NotBlank(message = "Store name is required")
    @Size(min = 2, max = 100, message = "Store name must be 2–100 characters")
    private String storeName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Phone is required")
    private String phone;

    private String businessAddress;

    private String businessRegistrationNumber;
}
