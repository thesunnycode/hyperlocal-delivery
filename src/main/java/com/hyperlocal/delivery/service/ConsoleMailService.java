package com.hyperlocal.delivery.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyperlocal.delivery.config.MailProperties;

import lombok.RequiredArgsConstructor;

/**
 * Fallback password-reset mailer: logs the reset link instead of sending a
 * real email. Used for local development and test, and as the prod fallback
 * when SMTP is not configured (see {@link com.hyperlocal.delivery.config.MailConfig}).
 */
@RequiredArgsConstructor
public class ConsoleMailService implements PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(ConsoleMailService.class);

    private final MailProperties mailProperties;

    @Override
    public void sendResetEmail(String toEmail, String rawToken) {
        String link = MailLinkBuilder.buildResetLink(mailProperties.resetLinkBaseUrl(), rawToken);
        log.info("[DEV MAIL] Password reset requested for {}. Reset link: {}", toEmail, link);
    }
}
