package com.hacisimsek.notification.controller;

import com.hacisimsek.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
public class TestController {

    private final NotificationService notificationService;

    @GetMapping("/email")
    public String testEmail() {

        notificationService.sendOrderShippedNotification(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "invydexter@gmail.com",
                "TEST123"
        );

        return "Email Sent";
    }
}
