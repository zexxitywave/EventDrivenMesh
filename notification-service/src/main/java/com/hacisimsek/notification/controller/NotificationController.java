package com.hacisimsek.notification.controller;

import com.hacisimsek.notification.model.Notification;
import com.hacisimsek.notification.repository.NotificationRepository;
import com.hacisimsek.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    /** Get all notifications for the logged-in user */
    @GetMapping
    public ResponseEntity<List<Notification>> getMyNotifications(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(notificationService.getNotificationsByRecipient(userId));
    }

    /** Get only unread notifications */
    @GetMapping("/unread")
    public ResponseEntity<List<Notification>> getUnreadNotifications(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(notificationService.getUnreadNotifications(userId));
    }

    /** Get unread notification count (for badge in UI) */
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(userId)));
    }

    /** Get all notifications for a specific order */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<Notification>> getNotificationsByOrder(
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(notificationService.getNotificationsByOrder(orderId));
    }

    /** Mark a single notification as read */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Map<String, String>> markAsRead(
            @PathVariable UUID id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(Map.of("message", "Notification marked as read"));
    }

    /** Mark all notifications as read for the logged-in user */
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead(
            @RequestHeader("X-User-Id") UUID userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }

    /**
     * Download the PDF invoice for an order.
     *
     * GET /api/notifications/invoice/{orderId}
     *
     * Returns the PDF as an inline/downloadable attachment.
     * The PDF is stored in MongoDB on the ORDER_PLACED notification record.
     *
     * Response:
     *   200 application/pdf  — invoice bytes
     *   404                  — no ORDER_PLACED notification found for this order
     *   422                  — notification exists but PDF was not generated (e.g. generation failed)
     */
    @GetMapping("/invoice/{orderId}")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable UUID orderId) {
        log.info("[Invoice] Download requested for orderId={}", orderId);

        // Find the ORDER_PLACED notification for this order
        return notificationRepository.findByOrderId(orderId)
                .stream()
                .filter(n -> n.getType() == Notification.NotificationType.ORDER_PLACED)
                .filter(n -> n.getInvoicePdf() != null && n.getInvoicePdf().length > 0)
                .findFirst()
                .map(notification -> {
                    String filename = "invoice-" + orderId + ".pdf";
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_PDF);
                    headers.setContentDisposition(
                            ContentDisposition.inline()
                                    .filename(filename)
                                    .build());
                    headers.setContentLength(notification.getInvoicePdf().length);

                    log.info("[Invoice] Serving PDF for orderId={}, size={} bytes",
                            orderId, notification.getInvoicePdf().length);
                    return new ResponseEntity<>(notification.getInvoicePdf(), headers, HttpStatus.OK);
                })
                .orElseGet(() -> {
                    // Check if notification exists but PDF is missing
                    boolean notificationExists = notificationRepository.findByOrderId(orderId)
                            .stream()
                            .anyMatch(n -> n.getType() == Notification.NotificationType.ORDER_PLACED);

                    if (notificationExists) {
                        log.warn("[Invoice] ORDER_PLACED notification found but no PDF for orderId={}", orderId);
                        return ResponseEntity.unprocessableEntity().<byte[]>build();
                    }

                    log.warn("[Invoice] No ORDER_PLACED notification found for orderId={}", orderId);
                    return ResponseEntity.notFound().<byte[]>build();
                });
    }
}

    /** Get all notifications for the logged-in user */
    @GetMapping
    public ResponseEntity<List<Notification>> getMyNotifications(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(notificationService.getNotificationsByRecipient(userId));
    }

    /** Get only unread notifications */
    @GetMapping("/unread")
    public ResponseEntity<List<Notification>> getUnreadNotifications(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(notificationService.getUnreadNotifications(userId));
    }

    /** Get unread notification count (for badge in UI) */
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(userId)));
    }

    /** Get all notifications for a specific order */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<Notification>> getNotificationsByOrder(
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(notificationService.getNotificationsByOrder(orderId));
    }

    /** Mark a single notification as read */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Map<String, String>> markAsRead(
            @PathVariable UUID id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(Map.of("message", "Notification marked as read"));
    }

    /** Mark all notifications as read for the logged-in user */
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead(
            @RequestHeader("X-User-Id") UUID userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }
}
