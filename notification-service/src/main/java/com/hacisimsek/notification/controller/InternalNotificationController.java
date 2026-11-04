package com.hacisimsek.notification.controller;

import com.hacisimsek.notification.dto.SendEmailRequest;
import com.hacisimsek.notification.model.Notification;
import com.hacisimsek.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal email endpoint for automation (n8n).
 *
 * <p>The API Gateway only routes {@code /api/v1/notifications/**} through JWT
 * auth; this endpoint is meant to be called service-to-service on the internal
 * network. In production, add a shared-secret header or network policy.</p>
 */
@RestController
@RequestMapping("/api/v1/notifications/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalNotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    public ResponseEntity<Notification> send(@Valid @RequestBody SendEmailRequest request) {
        log.info("Internal email request: type={}, recipient={}",
                request.getType(), request.getRecipientEmail());

        Notification notification = notificationService.sendCustomEmail(
                request.getRecipientId(),
                request.getRecipientEmail(),
                request.getSubject(),
                request.getMessage(),
                request.getType());

        return ResponseEntity.ok(notification);
    }
}
