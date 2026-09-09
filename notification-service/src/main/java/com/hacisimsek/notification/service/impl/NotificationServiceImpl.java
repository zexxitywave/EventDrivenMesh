package com.hacisimsek.notification.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.notification.model.Notification;
import com.hacisimsek.notification.model.NotificationDeadLetter;
import com.hacisimsek.notification.repository.NotificationRepository;
import com.hacisimsek.notification.service.InvoicePdfService;
import com.hacisimsek.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final InvoicePdfService invoicePdfService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.notification.from-email}")
    private String fromEmail;

    @Value("${app.notification.max-retry-attempts:3}")
    private int maxRetryAttempts;

    @Value("${app.notification.retry-base-delay-minutes:10}")
    private int retryBaseDelayMinutes;

    @Value("${app.notification.dlq-topic:notification-dlq}")
    private String dlqTopic;

    // ── Order Placed ──────────────────────────────────────────────────────────

    @Override
    public void sendOrderPlacedNotification(UUID orderId, UUID customerId, String email) {
        String subject = "Order Confirmed — Your order has been placed!";
        String message = """
                <html><body>
                <h2>Thank you for your order!</h2>
                <p>Your order <strong>%s</strong> has been placed successfully.</p>
                <p>We are processing it now. You will receive an update shortly.</p>
                </body></html>
                """.formatted(orderId);
        buildAndSend(customerId, orderId, email, subject, message,
                Notification.NotificationType.ORDER_PLACED, null);
    }

    /**
     * Full-detail overload triggered by the Kafka OrderCreatedEvent.
     * Generates a PDF invoice and attaches it to the confirmation email.
     */
    @Override
    @Async
    public void sendOrderPlacedNotification(OrderCreatedEvent event) {
        log.info("[Invoice] Processing OrderCreatedEvent for order={}", event.getOrderId());

        // 1. Generate PDF
        byte[] pdfBytes = null;
        try {
            pdfBytes = invoicePdfService.generateInvoice(event);
            log.info("[Invoice] PDF generated for order={}, size={} bytes",
                    event.getOrderId(), pdfBytes.length);
        } catch (Exception e) {
            log.error("[Invoice] PDF generation failed for order={}, sending email without attachment: {}",
                    event.getOrderId(), e.getMessage());
        }

        // 2. Build email body
        String subject = "Order Confirmed — Invoice #INV-"
                + event.getOrderId().toString().replace("-", "").substring(0, 10).toUpperCase();
        String message = """
                <html><body style="font-family:Arial,sans-serif;color:#0f172a;">
                <h2 style="color:#1e40af;">Thank you for your order!</h2>
                <p>Hi there,</p>
                <p>Your order <strong>%s</strong> has been placed successfully
                   with a total of <strong>Rs.%.2f</strong>.</p>
                <p>Please find your invoice attached to this email.</p>
                <p>We are processing your order now. You will receive a shipping
                   update once it is dispatched.</p>
                <br/>
                <p style="color:#64748b;font-size:12px;">
                  If you have any questions, contact us at support@zexxity.online
                </p>
                </body></html>
                """.formatted(event.getOrderId(),
                event.getTotalAmount() != null ? event.getTotalAmount() : java.math.BigDecimal.ZERO);

        // 3. Save notification with PDF bytes and send
        buildAndSend(event.getCustomerId(), event.getOrderId(),
                event.getCustomerEmail(), subject, message,
                Notification.NotificationType.ORDER_PLACED, pdfBytes);
    }

    @Override
    public void sendOrderCreatedNotification(UUID orderId, UUID customerId) {
        sendOrderPlacedNotification(orderId, customerId, null);
    }

    // ── Payment ───────────────────────────────────────────────────────────────

    @Override
    public void sendPaymentSuccessNotification(UUID orderId, UUID customerId, String email) {
        String subject = "Payment Successful ✓";
        String message = """
                <html><body>
                <h2>Payment Confirmed</h2>
                <p>Your payment for order <strong>%s</strong> was successful.</p>
                <p>Your order is now being prepared for shipment.</p>
                </body></html>
                """.formatted(orderId);
        buildAndSend(customerId, orderId, email, subject, message,
                Notification.NotificationType.PAYMENT_SUCCESS, null);
    }

    @Override
    public void sendPaymentFailedNotification(UUID orderId, UUID customerId, String email) {
        String subject = "Payment Failed — Action Required";
        String message = """
                <html><body>
                <h2>Payment Failed</h2>
                <p>We could not process your payment for order <strong>%s</strong>.</p>
                <p>Please update your payment details and try again.</p>
                </body></html>
                """.formatted(orderId);
        buildAndSend(customerId, orderId, email, subject, message,
                Notification.NotificationType.PAYMENT_FAILED, null);
    }

    // ── Shipping ──────────────────────────────────────────────────────────────

    @Override
    public void sendOrderShippedNotification(UUID orderId, UUID customerId,
                                              String email, String trackingNumber) {
        String subject = "Your Order Has Been Shipped!";
        String message = """
                <html><body>
                <h2>Order Shipped 🚚</h2>
                <p>Your order <strong>%s</strong> is on its way!</p>
                <p>Tracking Number: <strong>%s</strong></p>
                </body></html>
                """.formatted(orderId, trackingNumber);
        buildAndSend(customerId, orderId, email, subject, message,
                Notification.NotificationType.ORDER_SHIPPED, null);
    }

    @Override
    public void sendOrderShippedNotification(UUID orderId, UUID customerId, String trackingNumber) {
        sendOrderShippedNotification(orderId, customerId, null, trackingNumber);
    }

    @Override
    public void sendOrderDeliveredNotification(UUID orderId, UUID customerId, String email) {
        String subject = "Order Delivered ✓";
        String message = """
                <html><body>
                <h2>Order Delivered!</h2>
                <p>Your order <strong>%s</strong> has been delivered.</p>
                <p>We hope you enjoy your purchase! Please leave a review.</p>
                </body></html>
                """.formatted(orderId);
        buildAndSend(customerId, orderId, email, subject, message,
                Notification.NotificationType.ORDER_DELIVERED, null);
    }

    @Override
    public void sendOrderCancelledNotification(UUID orderId, UUID customerId, String email, String reason) {
        String subject = "Order Cancelled";
        String message = """
                <html><body style="font-family:Arial,sans-serif;">
                <h2 style="color:#dc2626;">Order Cancelled</h2>
                <p>Your order <strong>%s</strong> has been cancelled.</p>
                <p><strong>Reason:</strong> %s</p>
                <p>No payment was taken. If you have any questions contact support@zexxity.online</p>
                </body></html>
                """.formatted(orderId, reason != null ? reason : "Item out of stock");
        buildAndSend(customerId, orderId, email, subject, message,
                Notification.NotificationType.PAYMENT_FAILED, null);
    }

    @Override
    public void sendShipmentFailedNotification(UUID orderId, UUID customerId, String email, String reason) {
        String subject = "Shipment Failed — Refund Initiated";
        String message = """
                <html><body style="font-family:Arial,sans-serif;color:#0f172a;">
                <h2 style="color:#dc2626;">Shipment Failed</h2>
                <p>Unfortunately, we were unable to ship your order <strong>%s</strong>.</p>
                <p><strong>Reason:</strong> %s</p>
                <p>We have automatically initiated a <strong>full refund</strong> for your order.
                   The amount will be credited to your original payment method within 5-7 business days.</p>
                <p>We apologise for the inconvenience. If you have any questions,
                   contact us at support@zexxity.online</p>
                </body></html>
                """.formatted(orderId, reason != null ? reason : "Shipment could not be processed");
        buildAndSend(customerId, orderId, email, subject, message,
                Notification.NotificationType.PAYMENT_FAILED, null);
    }

    @Override
    public void sendRefundProcessedNotification(UUID orderId, UUID customerId, String email,
                                                 java.math.BigDecimal amount) {
        String subject = "Refund Processed — ₹" + (amount != null ? amount.toPlainString() : "");
        String message = """
                <html><body style="font-family:Arial,sans-serif;color:#0f172a;">
                <h2 style="color:#16a34a;">Refund Processed</h2>
                <p>Your refund of <strong>₹%s</strong> for order <strong>%s</strong> has been processed.</p>
                <p>The amount will be credited to your original payment method within 5-7 business days.</p>
                <p>If you have any questions, contact us at support@zexxity.online</p>
                </body></html>
                """.formatted(amount != null ? amount.toPlainString() : "N/A", orderId);
        buildAndSend(customerId, orderId, email, subject, message,
                Notification.NotificationType.PAYMENT_SUCCESS, null);
    }

    // ── OTP ───────────────────────────────────────────────────────────────────

    @Override
    public void sendOtpNotification(UUID recipientId, String email, String otp) {
        String subject = "Your Verification OTP: " + otp;
        String message = """
                <html><body>
                <h2>Email Verification</h2>
                <p>Your OTP is: <h1 style="letter-spacing:8px; color:#4F46E5;">%s</h1></p>
                <p>This OTP expires in <strong>15 minutes</strong>.</p>
                </body></html>
                """.formatted(otp);
        buildAndSend(recipientId, null, email, subject, message,
                Notification.NotificationType.OTP_VERIFICATION, null);
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    @Override
    public List<Notification> getNotificationsByRecipient(UUID recipientId) {
        return notificationRepository.findByRecipientId(recipientId);
    }

    @Override
    public List<Notification> getUnreadNotifications(UUID recipientId) {
        return notificationRepository.findByRecipientIdAndReadFalse(recipientId);
    }

    @Override
    public List<Notification> getNotificationsByOrder(UUID orderId) {
        return notificationRepository.findByOrderId(orderId);
    }

    @Override
    public long getUnreadCount(UUID recipientId) {
        return notificationRepository.countByRecipientIdAndReadFalse(recipientId);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @Override
    public void markAsRead(UUID notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            n.setUpdatedAt(LocalDateTime.now());
            notificationRepository.save(n);
        });
    }

    @Override
    public void markAllAsRead(UUID recipientId) {
        List<Notification> unread = notificationRepository.findByRecipientIdAndReadFalse(recipientId);
        unread.forEach(n -> {
            n.setRead(true);
            n.setUpdatedAt(LocalDateTime.now());
        });
        notificationRepository.saveAll(unread);
        log.info("Marked {} notifications as read for user: {}", unread.size(), recipientId);
    }

    // ── Retry Scheduler ───────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 300_000)
    public void retryFailedNotifications() {
        LocalDateTime now = LocalDateTime.now();
        List<Notification> failed = new ArrayList<>();
        // Backoff-aware candidates (nextAttemptAt passed) plus legacy rows written
        // before backoff scheduling existed (nextAttemptAt = null).
        failed.addAll(notificationRepository.findByStatusAndNextAttemptAtLessThanEqual(
                Notification.NotificationStatus.RETRY_PENDING, now));
        failed.addAll(notificationRepository.findByStatusAndNextAttemptAtIsNull(
                Notification.NotificationStatus.RETRY_PENDING));
        if (failed.isEmpty()) return;

        log.info("Retrying {} failed notifications", failed.size());
        failed.forEach(notification -> {
            if (notification.getRecipientEmail() == null) {
                publishToDlq(notification, "no recipient email to deliver", "NO_RECIPIENT");
                notification.setStatus(Notification.NotificationStatus.FAILED);
                notification.setNextAttemptAt(null);
                notification.setUpdatedAt(LocalDateTime.now());
                notificationRepository.save(notification);
                log.warn("Notification {} permanently failed — no recipient email", notification.getId());
                return;
            }
            sendEmail(notification);
        });
    }

    // ── Internal Helpers ──────────────────────────────────────────────────────

    @Async
    protected void buildAndSend(UUID recipientId, UUID orderId, String email,
                                 String subject, String message,
                                 Notification.NotificationType type, byte[] pdfBytes) {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .recipientId(recipientId)
                .orderId(orderId)
                .recipientEmail(email)
                .subject(subject)
                .message(message)
                .type(type)
                .invoicePdf(pdfBytes)
                .status(Notification.NotificationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
        log.info("Sending {} notification to user: {}", type, recipientId);

        if (email != null) {
            sendEmail(notification);
        } else {
            notification.setStatus(Notification.NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            notification.setUpdatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
    }

    private void sendEmail(Notification notification) {
        try {
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(notification.getRecipientEmail());
            helper.setSubject(notification.getSubject());
            helper.setText(notification.getMessage(), true);

            // Attach PDF invoice if present
            if (notification.getInvoicePdf() != null && notification.getInvoicePdf().length > 0) {
                String filename = "invoice-" + notification.getOrderId() + ".pdf";
                helper.addAttachment(filename,
                        new ByteArrayResource(notification.getInvoicePdf()),
                        "application/pdf");
                log.info("[Invoice] Attached {} ({} bytes) to email for order={}",
                        filename, notification.getInvoicePdf().length, notification.getOrderId());
            }

            mailSender.send(mimeMessage);

            notification.setStatus(Notification.NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            notification.setNextAttemptAt(null);
            notification.setErrorMessage(null);
            notification.setUpdatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
            log.info("Email sent to: {}", notification.getRecipientEmail());

        } catch (jakarta.mail.MessagingException | org.springframework.mail.MailException e) {
            String error = e.getMessage() != null ? e.getMessage() : e.toString();
            log.error("Failed to send email to {}: {}", notification.getRecipientEmail(), error);

            int attempt = notification.getRetryCount() + 1;
            notification.setRetryCount(attempt);
            notification.setErrorMessage(error);
            notification.setUpdatedAt(LocalDateTime.now());

            // Classify the failure: permanent rejection (bad address) vs transient
            // (provider quota / SMTP hiccup). Only permanent and exhausted-transient
            // failures go to the DLQ; transient ones keep a backoff schedule.
            boolean quota = isQuotaFailure(error);
            if (isPermanentFailure(error)) {
                publishToDlq(notification, error, "PERMANENT");
                notification.setStatus(Notification.NotificationStatus.FAILED);
                notification.setNextAttemptAt(null);
                log.warn("Notification {} permanently failed (permanent rejection): {}",
                        notification.getId(), error);
            } else {
                int cap = quota ? maxRetryAttempts * 2 + 1 : maxRetryAttempts;
                int backoff = quota ? quotaBackoffMinutes(attempt) : backoffMinutes(attempt);
                if (attempt >= cap) {
                    publishToDlq(notification, error, "TRANSIENT_EXHAUSTED");
                    notification.setStatus(Notification.NotificationStatus.FAILED);
                    notification.setNextAttemptAt(null);
                    log.warn("Notification {} permanently failed after {} retries",
                            notification.getId(), attempt);
                } else {
                    notification.setStatus(Notification.NotificationStatus.RETRY_PENDING);
                    notification.setNextAttemptAt(LocalDateTime.now().plusMinutes(backoff));
                    log.info("Notification {} retry #{} scheduled in ~{} min (transient, quota={})",
                            notification.getId(), attempt, backoff, quota);
                }
            }
            notificationRepository.save(notification);
        }
    }

    // ── Retry Classification & DLQ Helpers ───────────────────────────────────

    /**
     * Transient infra failures that should be retried later instead of failing hard.
     * Resend's "550 You have reached your daily email sending quota." is the main
     * offender — it matches {@code quota} and gets a long, bounded retry ladder.
     */
    private boolean isQuotaFailure(String errorMessage) {
        String m = (errorMessage == null ? "" : errorMessage).toLowerCase();
        return m.contains("quota")
                || m.contains("daily limit")
                || m.contains("rate limit")
                || m.contains("too many")
                || m.contains("throttl")
                || m.contains("421");
    }

    /**
     * Permanent rejections — retrying can never succeed (bad address, relay denied).
     * Anything not classified here defaults to transient, so we never drop mail
     * on an ambiguous SMTP response.
     */
    private boolean isPermanentFailure(String errorMessage) {
        String m = (errorMessage == null ? "" : errorMessage).toLowerCase();
        if (isQuotaFailure(errorMessage)) {
            return false;
        }
        return m.contains("invalid")
                || m.contains("user unknown")
                || m.contains("mailbox unavailable")
                || m.contains("address rejected")
                || m.contains("not accepted")
                || m.contains("relaying disallowed")
                || m.contains("5.1.1")
                || m.contains("5.1.3")
                || m.contains("5.5.4")
                || m.contains("permanent");
    }

    /** Exponential backoff for ordinary transient failures: 10, 20, 40, … capped at 4h. */
    private int backoffMinutes(int attempt) {
        long mins = (long) retryBaseDelayMinutes * (1L << Math.min(attempt - 1, 4));
        return (int) Math.min(mins, 240);
    }

    /** Longer ladder for provider quota: 30, 60, 120, 240, … capped at 6h. */
    private int quotaBackoffMinutes(int attempt) {
        long base = Math.max(retryBaseDelayMinutes, 30L);
        long mins = base * (1L << Math.min(attempt - 1, 4));
        return (int) Math.min(mins, 360);
    }

    /**
     * Publishes an undeliverable notification to the per-service DLQ topic so ops
     * can inspect/replay it. The DB row is still marked FAILED as the source of truth.
     */
    private void publishToDlq(Notification notification, String message, String reason) {
        NotificationDeadLetter dle = NotificationDeadLetter.builder()
                .notificationId(notification.getId())
                .orderId(notification.getOrderId())
                .recipientId(notification.getRecipientId())
                .recipientEmail(notification.getRecipientEmail())
                .subject(notification.getSubject())
                .type(notification.getType() != null ? notification.getType().name() : null)
                .retryCount(notification.getRetryCount())
                .reason(reason + (message != null ? ": " + message : ""))
                .createdAt(notification.getCreatedAt())
                .deadLetteredAt(LocalDateTime.now())
                .build();
        try {
            kafkaTemplate.send(dlqTopic, notification.getId().toString(), dle);
            log.info("[DLQ] Published notification {} to topic '{}' — {}", notification.getId(), dlqTopic, reason);
        } catch (Exception ex) {
            log.error("[DLQ] Failed publishing {} to '{}' — row remains FAILED in Mongo: {}",
                    notification.getId(), dlqTopic, ex.getMessage());
        }
    }
}
