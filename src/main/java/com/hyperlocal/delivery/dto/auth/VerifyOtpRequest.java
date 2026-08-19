package com.hyperlocal.delivery.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for OTP verification. Used by both registration and password
 * reset flows to submit the 6-digit code received via email.
 */
public record VerifyOtpRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\d{6}") String code
) {}
