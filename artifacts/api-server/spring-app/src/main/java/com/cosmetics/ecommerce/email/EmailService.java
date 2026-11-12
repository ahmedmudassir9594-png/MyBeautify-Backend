package com.cosmetics.ecommerce.email;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;
    private final String resetUrl;

    public EmailService(JavaMailSender mailSender, @Value("${app.mail.enabled:false}") boolean enabled,
                        @Value("${MAIL_FROM:no-reply@cosmetics.local}") String from,
                        @Value("${FRONTEND_RESET_URL:http://localhost:3000/reset-password}") String resetUrl) {
        this.mailSender = mailSender; this.enabled = enabled; this.from = from; this.resetUrl = resetUrl;
    }

    public void sendWelcome(String email, String firstName) {
        send(email, "Welcome to Cosmetics", "Hello " + firstName + ", your account is ready.");
    }

    public void sendPasswordReset(String email, String token) {
        send(email, "Reset your cosmetics account password", "Reset your password here: " + resetUrl + "?token=" + token);
    }

    public void sendOrderConfirmation(String email, String orderNumber, BigDecimal total) {
        send(email, "Order confirmation " + orderNumber, "Your order " + orderNumber + " was placed successfully. Total: " + total);
    }

    public void sendAdminAlert(String subject, String body) {
        send(from, subject, body);
    }

    private void send(String recipient, String subject, String body) {
        if (!enabled) return;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from); message.setTo(recipient); message.setSubject(subject); message.setText(body);
            mailSender.send(message);
        } catch (MailException exception) {
            log.warn("Email delivery failed for recipient domain {}.", recipient.substring(Math.max(0, recipient.indexOf('@') + 1)));
        }
    }
}