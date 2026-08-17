package com.hyperlocal.delivery.security;

import java.util.List;

import org.springframework.util.AntPathMatcher;

/**
 * Single source of truth for which {@code /api/**} (and related) paths are
 * reachable without a valid access token. Shared by
 * {@link com.hyperlocal.delivery.config.SecurityConfig} (which authorizes
 * them via {@code permitAll()}) and {@link JwtAuthFilter} (which must not
 * treat a stale or invalid {@code Authorization} header on one of these
 * paths as fatal — see
 * docs/audits/2026-09-10-flow-public-tracking.md for the bug this closes:
 * an expired or garbage bearer token was 401ing endpoints documented as
 * requiring no authentication at all, including public tracking).
 *
 * <p>Deliberately excludes the SPA static-asset/catch-all patterns in
 * {@code SecurityConfig} — those are never called with a meaningful
 * {@code Authorization} header, so they don't need this same-list
 * treatment in the filter.
 */
public final class PublicApiPaths {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static final List<String> PATTERNS = List.of(
            "/api/track/**",
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/resend-otp",
            "/api/auth/verify-registration-otp",
            "/api/auth/verify-reset-otp",
            "/api/auth/invite/*",
            "/api/auth/accept-invite",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/info",
            "/api/health");

    private PublicApiPaths() {
    }

    /** The raw ant-style patterns, for {@code SecurityConfig}'s own {@code requestMatchers(...)}. */
    public static String[] asArray() {
        return PATTERNS.toArray(new String[0]);
    }

    /** Whether {@code path} is reachable without authentication. */
    public static boolean matches(String path) {
        return PATTERNS.stream().anyMatch(p -> MATCHER.match(p, path));
    }
}
