package com.hyperlocal.delivery.service;

import com.hyperlocal.delivery.model.OtpPurpose;

/**
 * Composes and sends OTP verification emails. Two implementations exist:
 * <ul>
 *   <li>{@link SmtpOtpEmailService} — production, sends via Google SMTP</li>
 *   <li>{@link ConsoleOtpEmailService} — dev/test, logs to console</li>
 * </ul>
 */
public interface OtpEmailService {

    /**
     * Send an OTP code to the given email address.
     *
     * @param toEmail  recipient email address
     * @param otpCode  the plaintext 6-digit OTP code
     * @param purpose  the reason the OTP was generated
     * @throws com.hyperlocal.delivery.exception.MailDeliveryException if sending fails (logged, not retried)
     */
    void sendOtpEmail(String toEmail, String otpCode, OtpPurpose purpose);
}
