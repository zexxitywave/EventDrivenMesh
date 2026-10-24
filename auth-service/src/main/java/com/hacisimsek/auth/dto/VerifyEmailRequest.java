package com.hacisimsek.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyEmailRequest {

    @NotBlank @Email
    private String email;

    @NotBlank(message = "OTP is required")
    private String otp;
}
