package com.hyperlocal.delivery.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.hyperlocal.delivery.model.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Signs and verifies access JWTs (HS256) and mints opaque refresh tokens.
 *
 * <p>Claims carried on the access token: {@code sub} (email),
 * {@code userId}, {@code businessId}, {@code role}, {@code iat},
 * {@code exp}. Refresh tokens are random 256-bit strings issued in
 * Base64URL and stored at rest as lowercase-hex SHA-256 digests.
 */
@Component
public class JwtUtil {

    private static final int MIN_SECRET_BYTES = 32;
    private static final int REFRESH_BYTES = 32;

    private final JwtProperties props;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public JwtUtil(JwtProperties props) {
        this.props = props;
        if (props.secret() == null
                || props.secret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES
                            + " bytes for HS256; configure the JWT_SECRET env var with sufficient length");
        }
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Sign a new access token for the given principal.
     */
    public String signAccess(AuthenticatedPrincipal p) {
        Instant now = Instant.now();
        Instant exp = now.plus(accessTtl());
        return Jwts.builder()
                .subject(p.email())
                .claim("userId", p.userId())
                .claim("businessId", p.businessId())
                .claim("role", p.role() != null ? p.role().name() : null)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Parse and verify an access token's signature and expiration.
     *
     * @throws ExpiredJwtException if the token is past its {@code exp}
     * @throws JwtException        for any other validation failure
     *                             (tampered signature, malformed token,
     *                             unsupported algorithm)
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Pull the role claim off a parsed token, tolerating its absence.
     */
    public UserRole extractRole(Claims claims) {
        String role = claims.get("role", String.class);
        return role != null ? UserRole.valueOf(role) : null;
    }

    /**
     * Generate a fresh opaque token: 32 random bytes encoded as Base64URL
     * without padding. Used both for refresh tokens and password-reset
     * tokens — the entropy/format requirements are identical.
     */
    public String generateOpaqueToken() {
        byte[] buf = new byte[REFRESH_BYTES];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /**
     * Deterministic SHA-256 of the input, returned as a lowercase hex
     * string (64 chars). Used to store refresh tokens without the raw
     * value ever hitting the database.
     */
    public static String sha256Hex(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("raw token must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Configured access-token lifetime.
     */
    public Duration accessTtl() {
        return Duration.ofMinutes(props.accessTtlMinutes());
    }

    /**
     * Configured refresh-token lifetime.
     */
    public Duration refreshTtl() {
        return Duration.ofDays(props.refreshTtlDays());
    }

    /**
     * Sign a short-lived reset session token (JWT) after successful OTP
     * verification in the password-reset flow.
     *
     * <p>The token carries {@code sub} = email and a {@code purpose} claim
     * set to {@code "password_reset"}. It expires after 5 minutes.
     *
     * @param email the email address of the user resetting their password
     * @return a signed JWT string
     */
    public String signResetToken(String email) {
        Instant now = Instant.now();
        Instant exp = now.plus(RESET_TOKEN_TTL);
        return Jwts.builder()
                .subject(email)
                .claim("purpose", "password_reset")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Parse and validate a password-reset session token. Verifies the
     * signature, checks expiry, and confirms the {@code purpose} claim is
     * {@code "password_reset"}.
     *
     * @param token the JWT reset token string
     * @return the email address extracted from the token's subject claim
     * @throws JwtException if signature verification, expiry, or purpose
     *                      validation fails
     */
    public String parseResetToken(String token) {
        Claims claims = parseResetTokenClaims(token);
        return claims.getSubject();
    }

    /**
     * Parse and validate a password-reset session token, returning the full
     * claims (including {@code iat}) for replay protection checks.
     *
     * @param token the JWT reset token string
     * @return the parsed Claims object
     * @throws JwtException if signature verification, expiry, or purpose
     *                      validation fails
     */
    public Claims parseResetTokenClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String purpose = claims.get("purpose", String.class);
        if (!"password_reset".equals(purpose)) {
            throw new JwtException("Invalid token purpose: expected 'password_reset'");
        }

        return claims;
    }

    /** Reset session token lifetime: 5 minutes. */
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(5);
}
