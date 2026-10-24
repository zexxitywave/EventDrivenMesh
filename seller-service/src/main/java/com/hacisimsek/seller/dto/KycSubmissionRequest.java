package com.hacisimsek.seller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class KycSubmissionRequest {

    @NotBlank(message = "PAN number is required")
    @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]",
            message = "PAN must match the format ABCDE1234F")
    private String panNumber;

    // ── Aadhaar verification disabled ──────────────────────────────────────
    // Aadhaar collection + UIDAI verification is intentionally commented out;
    // KYC currently verifies only the PAN. Re-enable to require Aadhaar.
    //
    //    @NotBlank(message = "Aadhaar number is required")
    //    @Size(min = 12, max = 12, message = "Aadhaar must be exactly 12 digits")
    //    @Pattern(regexp = "[0-9]{12}", message = "Aadhaar must contain exactly 12 digits")
    //    private String aadhaarNumber;

    /** Full name exactly as printed on the PAN card. Used to match the PAN registry. */
    @NotBlank(message = "Full name as per PAN is required")
    private String fullName;

    /** Date of birth (yyyy-MM-dd), as per PAN card. Used to match the PAN registry. */
    @NotNull(message = "Date of birth as per PAN is required")
    @Past(message = "Date of birth must be in the past")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;
}