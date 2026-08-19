package com.hyperlocal.delivery.dto.auth;

import com.hyperlocal.delivery.model.OtpPurpose;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body to resend an OTP code. Invalidates the previous active OTP for
 * the given email and purpose, then generates and sends a fresh code.
 */
public record ResendOtpRequest(
        @NotBlank @Email String email,
        @NotNull OtpPurpose purpose
) {}
