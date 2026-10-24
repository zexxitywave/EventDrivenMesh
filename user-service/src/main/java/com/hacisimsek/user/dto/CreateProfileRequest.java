package com.hacisimsek.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Called by auth-service (or internally) after a new user registers,
 * to create the initial profile record.
 */
@Data
public class CreateProfileRequest {

    @NotBlank
    private String fullName;

    @NotBlank
    @Email
    private String email;

    private String phoneNumber;
}
