package com.hacisimsek.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private UUID id;

    @Indexed
    private UUID recipientId;

    @Indexed
    private UUID orderId;

    private String recipientEmail;
    private String subject;
    private String message;

    /**
     * Raw PDF bytes for ORDER_PLACED notifications.
     * Stored in MongoDB as binary — served by GET /api/notifications/invoice/{orderId}.
     * Null for all other notification types.
     */
    private byte[] invoicePdf;

    private NotificationType type;
    private NotificationStatus status;

    /** Number of email send attempts (for retry logic) */
    @Builder.Default
    private int retryCount = 0;

    /** Max retries before giving up */
    @Builder.Default
    private int maxRetries = 3;

    /** Whether the user has read this notification */
    @Builder.Default
    private boolean read = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime sentAt;

    public enum NotificationType {
        ORDER_PLACED,
        ORDER_CONFIRMED,
        PAYMENT_SUCCESS,
        PAYMENT_FAILED,
        ORDER_SHIPPED,
        ORDER_DELIVERED,
        OTP_VERIFICATION,
        PASSWORD_RESET
    }

    public enum NotificationStatus {
        PENDING,
        SENT,
        FAILED,
        RETRY_PENDING
    }
}
