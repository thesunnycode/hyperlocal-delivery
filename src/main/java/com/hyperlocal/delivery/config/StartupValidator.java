package com.hyperlocal.delivery.config;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.hyperlocal.delivery.security.JwtProperties;

/**
 * Fail-fast validation of critical configuration at startup.
 *
 * <p>Runs once on {@link ApplicationReadyEvent}. {@link JwtProperties}
 * is additionally validated inside {@code JwtUtil}'s constructor as
 * defence-in-depth; this hook exists to give operators a clear,
 * top-level error message before any traffic is served.
 */
@Component
public class StartupValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupValidator.class);
    private static final int MIN_JWT_SECRET_BYTES = 32;

    private final JwtProperties jwtProperties;

    public StartupValidator(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * Assert every required secret / configuration value is present and
     * well-formed. Logs an INFO line on success; throws
     * {@link IllegalStateException} on failure (which aborts startup).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateOnStartup() {
        String secret = jwtProperties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least " + MIN_JWT_SECRET_BYTES
                            + " bytes for HS256; configure the JWT_SECRET env var with sufficient length");
        }
        log.info("Startup validation passed: JWT secret length OK, access TTL {}m, refresh TTL {}d",
                jwtProperties.accessTtlMinutes(), jwtProperties.refreshTtlDays());
    }
}
