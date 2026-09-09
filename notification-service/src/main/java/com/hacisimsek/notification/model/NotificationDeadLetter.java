package com.hacisimsek.notification.model;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Slim snapshot of a notification that could not be delivered even after the
 * retry ladder. Published to the {@code notification-dlq} Kafka topic so ops
 * can inspect, replay, or alert on undeliverable mail without GUI/DB spelunking.
 * Intentionally excludes the invoice PDF bytes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDeadLetter {

    private UUID notificationId;
    private UUID orderId;
    private UUID recipientId;
    private String recipientEmail;
    private String subject;
    private String type;
    private int retryCount;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime deadLetteredAt;
}