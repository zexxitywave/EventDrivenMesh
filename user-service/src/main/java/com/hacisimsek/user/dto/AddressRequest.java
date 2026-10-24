package com.hacisimsek.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddressRequest {

    @NotBlank
    private String label;           // "Home", "Office", etc.

    @NotBlank
    private String recipientName;

    @NotBlank
    private String phoneNumber;

    @NotBlank
    private String addressLine1;

    private String addressLine2;

    @NotBlank
    private String city;

    @NotBlank
    private String state;

    @NotBlank
    private String postalCode;

    @NotBlank
    private String country;

    private boolean defaultAddress;
}
