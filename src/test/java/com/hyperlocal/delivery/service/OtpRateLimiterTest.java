package com.hyperlocal.delivery.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hyperlocal.delivery.exception.RateLimitExceededException;
import com.hyperlocal.delivery.model.OtpPurpose;

class OtpRateLimiterTest {

    private OtpRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new OtpRateLimiter();
    }

    @Test
    void checkRateLimit_withinLimit_doesNotThrow() {
        // Should allow up to MAX_REQUESTS calls without throwing
        for (int i = 0; i < OtpRateLimiter.MAX_REQUESTS; i++) {
            assertDoesNotThrow(() ->
                    rateLimiter.checkRateLimit("user@example.com", OtpPurpose.REGISTRATION));
        }
    }

    @Test
    void checkRateLimit_exceedsLimit_throws() {
        // Fill the window
        for (int i = 0; i < OtpRateLimiter.MAX_REQUESTS; i++) {
            rateLimiter.checkRateLimit("user@example.com", OtpPurpose.REGISTRATION);
        }

        // Next call should throw
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                () -> rateLimiter.checkRateLimit("user@example.com", OtpPurpose.REGISTRATION));

        // retryAfter should be positive
        assertTrue(ex.getRetryAfterSeconds() > 0);
    }

    @Test
    void checkRateLimit_differentEmails_areIndependent() {
        // Fill limit for email A
        for (int i = 0; i < OtpRateLimiter.MAX_REQUESTS; i++) {
            rateLimiter.checkRateLimit("a@example.com", OtpPurpose.REGISTRATION);
        }

        // Email B should still work fine
        assertDoesNotThrow(() ->
                rateLimiter.checkRateLimit("b@example.com", OtpPurpose.REGISTRATION));
    }

    @Test
    void checkRateLimit_differentPurposes_areIndependent() {
        // Fill limit for REGISTRATION
        for (int i = 0; i < OtpRateLimiter.MAX_REQUESTS; i++) {
            rateLimiter.checkRateLimit("user@example.com", OtpPurpose.REGISTRATION);
        }

        // PASSWORD_RESET should still work fine
        assertDoesNotThrow(() ->
                rateLimiter.checkRateLimit("user@example.com", OtpPurpose.PASSWORD_RESET));
    }

    @Test
    void checkRateLimit_normalizesEmail_caseInsensitive() {
        // Mix case — should count as same key
        for (int i = 0; i < OtpRateLimiter.MAX_REQUESTS; i++) {
            rateLimiter.checkRateLimit(
                    i % 2 == 0 ? "User@Example.COM" : "user@example.com",
                    OtpPurpose.REGISTRATION);
        }

        // Should be at the limit now regardless of case
        assertThrows(RateLimitExceededException.class,
                () -> rateLimiter.checkRateLimit("USER@EXAMPLE.COM", OtpPurpose.REGISTRATION));
    }

    @Test
    void evictStaleEntries_removesEmptyKeys() {
        // Add some entries then evict — since the window is 15min, entries
        // just added won't be evicted, but we can verify the method runs
        // without error (defensive — ensures no ConcurrentModificationException)
        rateLimiter.checkRateLimit("user@example.com", OtpPurpose.REGISTRATION);
        assertDoesNotThrow(() -> rateLimiter.evictStaleEntries());
    }
}
