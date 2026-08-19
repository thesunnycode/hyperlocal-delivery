package com.hyperlocal.delivery.dto.auth;

/**
 * Response envelope returned after a successful token refresh. Contains
 * the new access/refresh token pair and the access token's TTL in seconds.
 */
public record TokenRefreshResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {}
