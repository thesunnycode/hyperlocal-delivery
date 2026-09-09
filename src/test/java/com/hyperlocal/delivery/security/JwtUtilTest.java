package com.hyperlocal.delivery.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hyperlocal.delivery.model.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Unit tests for {@link JwtUtil}.
 */
class JwtUtilTest {

    private static final String TEST_SECRET = "this-is-a-test-secret-that-is-at-least-32-bytes-long-for-hs256";
    private static final long ACCESS_TTL_MINUTES = 60;
    private static final long REFRESH_TTL_DAYS = 7;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(TEST_SECRET, ACCESS_TTL_MINUTES, REFRESH_TTL_DAYS);
        jwtUtil = new JwtUtil(props);
    }

    @Test
    void signAccess_and_parse_roundTrip() {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                42L, "user@example.com", 7L, UserRole.BUSINESS_OWNER);

        String token = jwtUtil.signAccess(principal);
        assertNotNull(token);

        Claims claims = jwtUtil.parse(token);
        assertEquals("user@example.com", claims.getSubject());
        assertEquals(42L, claims.get("userId", Long.class));
        assertEquals(7L, claims.get("businessId", Long.class));
        assertEquals("BUSINESS_OWNER", claims.get("role", String.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    @Test
    void parse_expiredToken_throws() {
        // Create a token that is already expired
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String expiredToken = Jwts.builder()
                .subject("expired@example.com")
                .claim("userId", 1L)
                .claim("businessId", 1L)
                .claim("role", "BUSINESS_OWNER")
                .issuedAt(Date.from(now.minusSeconds(3600)))
                .expiration(Date.from(now.minusSeconds(1800)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThrows(ExpiredJwtException.class, () -> jwtUtil.parse(expiredToken));
    }

    @Test
    void parse_tamperedToken_throws() {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                1L, "test@example.com", 1L, UserRole.DELIVERY_AGENT);
        String token = jwtUtil.signAccess(principal);

        // Tamper with the token by modifying a character in the signature
        char[] chars = token.toCharArray();
        int lastDot = token.lastIndexOf('.');
        int tamperIdx = lastDot + 1;
        chars[tamperIdx] = chars[tamperIdx] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);

        assertThrows(JwtException.class, () -> jwtUtil.parse(tampered));
    }

    @Test
    void sha256Hex_deterministic() {
        String input = "some-refresh-token-value";
        String hash1 = JwtUtil.sha256Hex(input);
        String hash2 = JwtUtil.sha256Hex(input);

        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
        assertTrue(hash1.matches("[0-9a-f]{64}"));
    }

    @Test
    void sha256Hex_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> JwtUtil.sha256Hex(null));
    }

    @Test
    void generateOpaqueToken_length() {
        String refresh = jwtUtil.generateOpaqueToken();
        assertNotNull(refresh);

        // Decode from Base64URL and verify at least 32 bytes
        byte[] decoded = Base64.getUrlDecoder().decode(refresh);
        assertTrue(decoded.length >= 32,
                "Decoded refresh token should be at least 32 bytes, got " + decoded.length);
    }
}
