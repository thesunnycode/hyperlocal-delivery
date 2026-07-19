package com.hyperlocal.delivery.dto.auth;

/**
 * Response returned after successful OTP verification in the password reset
 * flow. Contains a short-lived JWT reset session token (5-minute TTL) that
 * authorizes setting a new password.
 */
public record ResetTokenResponse(String resetToken) {}
