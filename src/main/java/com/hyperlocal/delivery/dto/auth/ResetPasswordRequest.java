package com.hyperlocal.delivery.dto.auth;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body to redeem a password-reset session token (JWT) and set a new
 * password. The {@code resetToken} is a short-lived JWT issued after successful
 * OTP verification in the password-reset flow.
 *
 * <p>Accepts {@code newPassword} (this record's canonical field name) or the
 * frontend's {@code password} key via {@link JsonAlias}.
 */
public record ResetPasswordRequest(
        @NotBlank @JsonAlias("token") String resetToken,
        @NotBlank @Size(min = 8) @JsonAlias("password") String newPassword
) {}
