package com.hyperlocal.delivery.service;

/**
 * Sends the password-reset email. Two implementations: a console-logging
 * one for local/test use ({@link ConsoleMailService}) and a real SMTP one
 * for production ({@link SmtpMailService}).
 */
public interface PasswordResetMailer {

    /**
     * Send (or log) the password-reset link for the given raw token.
     *
     * @param toEmail  recipient address
     * @param rawToken the plaintext reset token — never persisted, only its
     *                 hash is stored server-side
     */
    void sendResetEmail(String toEmail, String rawToken);
}
