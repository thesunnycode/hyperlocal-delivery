package com.hyperlocal.delivery.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

/**
 * Extracts and verifies the bearer access token on every request, and
 * populates the {@link SecurityContextHolder} with a
 * {@link CustomUserDetails} authentication on success.
 *
 * <p>Requests without an {@code Authorization: Bearer ...} header are
 * passed through untouched so that public endpoints and Spring
 * Security's own rejection path can run. Invalid or expired tokens are
 * short-circuited with a 401 {@link com.hyperlocal.delivery.dto.common.ApiError}
 * response — <b>unless</b> the request targets one of
 * {@link PublicApiPaths}, in which case the bad token is ignored and the
 * request proceeds unauthenticated, exactly as if no header had been sent
 * at all.
 *
 * <p>That carve-out exists because this filter runs before Spring
 * Security's own {@code permitAll()} authorization check: without it, a
 * stale or expired bearer token (which any generic HTTP client that
 * unconditionally reattaches a previously-stored token will send —
 * routinely, once the 15-minute access-token TTL elapses) 401s an
 * endpoint that is documented as requiring no authentication at all —
 * confirmed live against {@code GET /api/track/{token}} in
 * docs/audits/2026-09-10-flow-public-tracking.md.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(
            JwtUtil jwtUtil,
            CustomUserDetailsService userDetailsService,
            ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIX.length()).trim();
        if (token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (ExpiredJwtException ex) {
            log.debug("Rejected expired JWT on {}", request.getRequestURI());
            reject(request, response, chain, "Access token expired");
            return;
        } catch (JwtException ex) {
            log.debug("Rejected invalid JWT on {}: {}", request.getRequestURI(), ex.getMessage());
            reject(request, response, chain, "Invalid access token");
            return;
        }

        String email = claims.getSubject();
        if (email == null || email.isBlank()) {
            reject(request, response, chain, "Invalid access token");
            return;
        }

        // Reject non-access tokens (e.g. password-reset session tokens)
        // that happen to be signed with the same key. A reset token carries
        // a "purpose" claim — access tokens never do.
        if (claims.get("purpose") != null) {
            reject(request, response, chain, "Invalid access token");
            return;
        }

        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(email);
        } catch (UsernameNotFoundException ex) {
            reject(request, response, chain, "Account no longer exists");
            return;
        }

        if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
            reject(request, response, chain, "Account is not active");
            return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }

    /**
     * A bearer token that failed validation. On a genuinely protected path
     * this is fatal — write the 401 and stop. On one of
     * {@link PublicApiPaths}, the endpoint doesn't require authentication in
     * the first place, so the bad token is simply ignored and the request
     * proceeds unauthenticated, exactly as if no {@code Authorization}
     * header had been sent at all.
     */
    private void reject(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain, String message)
            throws ServletException, IOException {
        if (PublicApiPaths.matches(request.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }
        JwtAuthenticationEntryPoint.write(response, request, message, objectMapper);
    }
}
