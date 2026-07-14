package com.hyperlocal.delivery.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding of the {@code app.jwt.*} configuration block.
 *
 * @param secret            HMAC secret backing HS256 signing/verification;
 *                          must be at least 32 bytes (256 bits) of UTF-8
 *                          encoded material
 * @param accessTtlMinutes  access-token time-to-live, in minutes
 * @param refreshTtlDays    refresh-token time-to-live, in days
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessTtlMinutes,
        long refreshTtlDays) {
}
