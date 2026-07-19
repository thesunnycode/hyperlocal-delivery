package com.hyperlocal.delivery.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for email/password authentication.
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
