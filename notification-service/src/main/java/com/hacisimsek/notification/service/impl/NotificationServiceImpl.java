package com.hacisimsek.notification.service.impl;

import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.notification.model.Notification;
import com.hacisimsek.notification.repository.NotificationRepository;
import com.hacisimsek.notification.service.InvoicePdfService;
import com.hacisimsek.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final InvoicePdfService invoicePdfService;

    @Value("${app.notification.from-email}")
    private String fromEmail;

    @Value("${app.notification.max-retry-attempts:3}")
    private int maxRetryAttempts;

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
        List<Notification> failed = notificationRepository
                .findByStatus(Notification.NotificationStatus.RETRY_PENDING);
        if (failed.isEmpty()) return;

        log.info("Retrying {} failed notifications", failed.size());
        failed.forEach(notification -> {
            if (notification.getRetryCount() >= maxRetryAttempts) {
                notification.setStatus(Notification.NotificationStatus.FAILED);
                notification.setUpdatedAt(LocalDateTime.now());
                notificationRepository.save(notification);
                log.warn("Notification {} permanently failed after {} retries",
                        notification.getId(), notification.getRetryCount());
                return;
            }
            if (notification.getRecipientEmail() != null) {
                sendEmail(notification);
            }
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
            notification.setUpdatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
            log.info("Email sent to: {}", notification.getRecipientEmail());

        } catch (jakarta.mail.MessagingException | org.springframework.mail.MailException e) {
            log.error("Failed to send email to {}: {}", notification.getRecipientEmail(), e.getMessage());
            notification.setRetryCount(notification.getRetryCount() + 1);
            notification.setStatus(Notification.NotificationStatus.RETRY_PENDING);
            notification.setUpdatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
    }
}
