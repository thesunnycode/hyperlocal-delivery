package com.hyperlocal.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code app.cors.*} configuration block.
 *
 * @param allowedOrigins comma-separated list of origins permitted by
 *                       CORS, or {@code "*"} for wildcard (in which
 *                       case credentials are disallowed by the browser
 *                       per CORS spec)
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsConfigProperties(String allowedOrigins) {
}
