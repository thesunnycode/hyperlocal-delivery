package com.hyperlocal.delivery.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for exchanging a refresh token for a new access/refresh pair.
 */
public record RefreshRequest(
        @NotBlank String refreshToken
) {}
