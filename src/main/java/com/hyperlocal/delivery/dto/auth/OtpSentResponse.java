package com.hyperlocal.delivery.dto.auth;

/**
 * Response returned after successfully initiating OTP generation and email
 * delivery. Contains a user-facing message and the email address the code was
 * sent to.
 */
public record OtpSentResponse(String message, String email) {}
