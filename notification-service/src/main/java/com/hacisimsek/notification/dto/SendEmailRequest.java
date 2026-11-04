package com.hacisimsek.notification.dto;

import com.hacisimsek.notification.model.Notification;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Body for {@code POST /api/v1/notifications/internal/send}.
 * Used by automation (the n8n abandoned-cart workflow) to send a one-off email
 * without going through a Kafka saga event.
 */
@Data
public class SendEmailRequest {

    @NotNull
    private UUID recipientId;

    @NotBlank
    @Email
    private String recipientEmail;

    @NotBlank
    private String subject;

    @NotBlank
    private String message;

    @NotNull
    private Notification.NotificationType type;
}
