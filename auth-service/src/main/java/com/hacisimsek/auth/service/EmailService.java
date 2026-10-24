package com.hacisimsek.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.from}")
    private String from;

    @Async
    public void sendVerificationOtp(String toEmail, String name, String otp) {
        String subject = "Verify your email — OTP: " + otp;
        String body = """
                <html><body>
                <h2>Hi %s,</h2>
                <p>Your email verification OTP is:</p>
                <h1 style="letter-spacing:8px; color:#4F46E5;">%s</h1>
                <p>This OTP expires in <strong>15 minutes</strong>.</p>
                <p>If you did not sign up, please ignore this email.</p>
                </body></html>
                """.formatted(name, otp);
        sendHtmlEmail(toEmail, subject, body);
    }

    @Async
    public void sendPasswordResetOtp(String toEmail, String name, String otp) {
        String subject = "Reset your password — OTP: " + otp;
        String body = """
                <html><body>
                <h2>Hi %s,</h2>
                <p>Your password reset OTP is:</p>
                <h1 style="letter-spacing:8px; color:#EF4444;">%s</h1>
                <p>This OTP expires in <strong>15 minutes</strong>.</p>
                <p>If you did not request this, please ignore this email.</p>
                </body></html>
                """.formatted(name, otp);
        sendHtmlEmail(toEmail, subject, body);
    }

    private void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent to: {}", to);
        } catch (jakarta.mail.MessagingException | org.springframework.mail.MailException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
