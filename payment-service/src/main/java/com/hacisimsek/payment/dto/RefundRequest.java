package com.hacisimsek.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class RefundRequest {

    @NotNull(message = "paymentId is required")
    private UUID paymentId;

    /**
     * Amount to refund. If null or equal to payment amount, a full refund is processed.
     * If less than payment amount, a partial refund is processed.
     */
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "reason is required")
    private String reason;
}
