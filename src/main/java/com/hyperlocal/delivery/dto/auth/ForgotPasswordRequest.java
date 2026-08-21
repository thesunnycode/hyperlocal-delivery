package com.hyperlocal.delivery.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body to begin the password-reset flow (X2).
 */
public record ForgotPasswordRequest(@NotBlank @Email String email) {}
