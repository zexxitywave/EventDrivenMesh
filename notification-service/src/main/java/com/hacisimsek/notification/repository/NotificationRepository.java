package com.hacisimsek.notification.repository;

import com.hacisimsek.notification.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends MongoRepository<Notification, UUID> {

    List<Notification> findByRecipientId(UUID recipientId);

    List<Notification> findByOrderId(UUID orderId);

    List<Notification> findByRecipientIdAndReadFalse(UUID recipientId);

    long countByRecipientIdAndReadFalse(UUID recipientId);

    List<Notification> findByStatus(Notification.NotificationStatus status);

    /**
     * Retry candidates whose scheduled time (nextAttemptAt) has passed.
     */
    List<Notification> findByStatusAndNextAttemptAtLessThanEqual(
            Notification.NotificationStatus status, LocalDateTime time);

    /**
     * Retry candidates written before backoff scheduling existed (nextAttemptAt = null).
     * Picked up immediately so legacy rows are not silently dropped.
     */
    List<Notification> findByStatusAndNextAttemptAtIsNull(Notification.NotificationStatus status);
}
