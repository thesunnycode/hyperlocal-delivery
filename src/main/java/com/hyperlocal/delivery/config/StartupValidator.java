package com.hyperlocal.delivery.config;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Fail-fast validation of production-only configuration.
 *
 * <p>The problem this solves: {@code application-prod.yml} binds
 * {@code app.cors.allowed-origins: ${CORS_ORIGINS}} and
 * {@code app.mail.invite-link-base-url: ${INVITE_LINK_BASE_URL}} with no
 * default, but Spring does not treat an unresolvable placeholder in a bound
 * property as an error &mdash; it binds the literal text {@code ${CORS_ORIGINS}}
 * instead. Without this check a prod deploy that forgot either variable
 * starts normally, then rejects every cross-origin call with 403 and hands
 * out invite links that contain the placeholder text.
 *
 * <p>Runs in {@link #afterPropertiesSet()}, i.e. while the application
 * context is being refreshed, so a failure aborts startup before the
 * embedded web server begins accepting connections. (It is not ordered
 * ahead of the datasource or JPA, so an unreachable database still reports
 * its own error first.) It is active only when
 * the {@code prod} profile is; {@code dev}, {@code test} and
 * {@code mysql-test} have working defaults and are not checked.
 *
 * <p>The JWT secret is deliberately not checked here. {@code JwtUtil}'s
 * constructor rejects a missing or shorter-than-32-byte secret in every
 * profile and that failure already aborts startup; a second copy of the
 * check in this class would only ever be dead code.
 */
@Component
public class StartupValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(StartupValidator.class);

    private final CorsConfigProperties corsProperties;
    private final MailProperties mailProperties;
    private final Environment environment;

    public StartupValidator(
            CorsConfigProperties corsProperties,
            MailProperties mailProperties,
            Environment environment) {
        this.corsProperties = corsProperties;
        this.mailProperties = mailProperties;
        this.environment = environment;
    }

    /**
     * In the {@code prod} profile, assert that every required environment
     * variable actually reached its bound property. Throws
     * {@link IllegalStateException} listing every problem at once (which
     * aborts startup); logs an INFO line on success.
     */
    @Override
    public void afterPropertiesSet() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> problems = new ArrayList<>();
        requireSet("app.cors.allowed-origins", "CORS_ORIGINS",
                corsProperties.allowedOrigins(), problems);
        requireSet("app.mail.invite-link-base-url", "INVITE_LINK_BASE_URL",
                mailProperties.inviteLinkBaseUrl(), problems);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Production configuration is incomplete: " + String.join("; ", problems));
        }
        log.info("Startup validation passed: required production settings are present");
    }

    private static void requireSet(String property, String envVar, String value, List<String> problems) {
        if (value == null || value.isBlank()) {
            problems.add(property + " is blank; set the " + envVar + " environment variable");
        } else if (value.contains("${")) {
            problems.add(property + " is the unresolved placeholder '" + value.trim()
                    + "'; set the " + envVar + " environment variable");
        }
    }
}
