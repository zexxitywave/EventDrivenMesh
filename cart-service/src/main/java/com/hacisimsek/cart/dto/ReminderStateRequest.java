package com.hacisimsek.cart.dto;

import com.hacisimsek.cart.model.ReminderStage;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Body for {@code PATCH /api/v1/cart/internal/{userId}/reminder-state}. */
@Data
public class ReminderStateRequest {

    @NotNull
    private ReminderStage stage;
}
