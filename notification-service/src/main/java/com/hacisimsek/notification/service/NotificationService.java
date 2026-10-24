package com.hacisimsek.notification.service;

import java.util.List;
import java.util.UUID;

import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.notification.model.Notification;

public interface NotificationService {

    // ── Saga-triggered notifications ──────────────────────────────────────────
    void sendOrderPlacedNotification(UUID orderId, UUID customerId, String email);

    /**
     * Full-detail overload — receives the complete OrderCreatedEvent so the
     * invoice PDF can be generated and attached to the confirmation email.
     */
    void sendOrderPlacedNotification(OrderCreatedEvent event);

    void sendPaymentSuccessNotification(UUID orderId, UUID customerId, String email);
    void sendPaymentFailedNotification(UUID orderId, UUID customerId, String email);
    void sendOrderShippedNotification(UUID orderId, UUID customerId, String email, String trackingNumber);
    void sendOrderDeliveredNotification(UUID orderId, UUID customerId, String email);
    void sendOrderCancelledNotification(UUID orderId, UUID customerId, String email, String reason);
    void sendShipmentFailedNotification(UUID orderId, UUID customerId, String email, String reason);
    void sendRefundProcessedNotification(UUID orderId, UUID customerId, String email, java.math.BigDecimal amount);

    // ── Auth-triggered notifications ──────────────────────────────────────────
    void sendOtpNotification(UUID recipientId, String email, String otp);

    // ── Query ─────────────────────────────────────────────────────────────────
    List<Notification> getNotificationsByRecipient(UUID recipientId);
    List<Notification> getUnreadNotifications(UUID recipientId);
    List<Notification> getNotificationsByOrder(UUID orderId);
    long getUnreadCount(UUID recipientId);

    // ── Actions ───────────────────────────────────────────────────────────────
    void markAsRead(UUID notificationId);
    void markAllAsRead(UUID recipientId);
    void retryFailedNotifications();

    // ── Legacy fallback ───────────────────────────────────────────────────────
    void sendOrderCreatedNotification(UUID orderId, UUID customerId);
    void sendOrderShippedNotification(UUID orderId, UUID customerId, String trackingNumber);
}
