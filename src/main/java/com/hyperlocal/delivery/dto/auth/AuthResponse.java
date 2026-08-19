package com.hyperlocal.delivery.dto.auth;

/**
 * Response envelope returned after successful registration or login.
 * Contains both tokens and the authenticated user's profile.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String token,
        String role,
        UserResponse user
) {

    /**
     * Builds an AuthResponse, deriving the top-level {@code token} (alias of
     * {@code accessToken}) and {@code role} (copy of {@code user.role()})
     * fields the frontend's AuthContext destructures directly.
     */
    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
        return new AuthResponse(accessToken, refreshToken, expiresIn, accessToken, user.role(), user);
    }
}
