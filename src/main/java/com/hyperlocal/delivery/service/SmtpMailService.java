package com.hyperlocal.delivery.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.hyperlocal.delivery.config.MailProperties;

import lombok.RequiredArgsConstructor;

/**
 * Production password-reset mailer. Sends a real email via the configured
 * {@link JavaMailSender} (SMTP host/credentials from {@code spring.mail.*}).
 * Only registered when {@code spring.mail.host} is actually set — see
 * {@link com.hyperlocal.delivery.config.MailConfig}.
 */
@RequiredArgsConstructor
public class SmtpMailService implements PasswordResetMailer {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    @Override
    public void sendResetEmail(String toEmail, String rawToken) {
        String link = MailLinkBuilder.buildResetLink(mailProperties.resetLinkBaseUrl(), rawToken);
        int ttlMinutes = mailProperties.resetTokenTtlMinutes();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Reset your Hyperlocal Delivery password");
        message.setText("Reset your password: " + link
                + "\n\nThis link expires in " + ttlMinutes
                + " minutes. If you didn't request this, ignore this email.");
        mailSender.send(message);
    }
}
