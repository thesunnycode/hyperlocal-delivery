package com.hyperlocal.delivery.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} method execution and {@code @Async}
 * support across the application context.
 *
 * <p>The {@code @Async} support is available for non-blocking work (e.g.,
 * fire-and-forget email notifications) but is not currently used for the
 * critical OTP email path — that stays synchronous so registration/reset
 * flows get immediate delivery feedback.
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
    // Enables @Scheduled methods and @Async across the application
}
