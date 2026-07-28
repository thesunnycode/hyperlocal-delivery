package com.hyperlocal.delivery.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hyperlocal.delivery.exception.RateLimitExceededException;

/**
 * In-memory sliding-window rate limiter for login attempts, keyed by email.
 * Prevents credential brute-force attacks by limiting failed attempts.
 *
 * <p>Allows {@value #MAX_ATTEMPTS} login attempts per email within a
 * {@value #WINDOW_MINUTES}-minute window. Only failed attempts should be
 * recorded (call {@link #recordFailedAttempt} on wrong-password/not-found).
 * Successful logins reset the counter via {@link #recordSuccess}.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_ATTEMPTS = 10;
    static final int WINDOW_MINUTES = 15;
    private static final Duration WINDOW = Duration.ofMinutes(WINDOW_MINUTES);

    private final ConcurrentHashMap<String, Deque<Instant>> failedAttempts = new ConcurrentHashMap<>();

    /**
     * Check whether a login attempt is allowed for this email. Call before
     * authenticating — rejects early if too many recent failures.
     *
     * @throws RateLimitExceededException if the limit is exceeded
     */
    public void checkAllowed(String email) {
        String key = email.trim().toLowerCase();
        Deque<Instant> timestamps = failedAttempts.get(key);
        if (timestamps == null) return;

        Instant windowStart = Instant.now().minus(WINDOW);
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= MAX_ATTEMPTS) {
            Instant oldest = timestamps.peekFirst();
            long retryAfter = Duration.between(Instant.now(), oldest.plus(WINDOW)).getSeconds();
            if (retryAfter <= 0) retryAfter = 1;
            throw new RateLimitExceededException(retryAfter);
        }
    }

    /**
     * Record a failed login attempt for this email.
     */
    public void recordFailedAttempt(String email) {
        String key = email.trim().toLowerCase();
        failedAttempts.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>()).addLast(Instant.now());
    }

    /**
     * Clear the failure counter on successful login (prevents
     * locked-out users from staying locked after correct credentials).
     */
    public void recordSuccess(String email) {
        String key = email.trim().toLowerCase();
        failedAttempts.remove(key);
    }

    /**
     * Periodic cleanup of stale entries.
     */
    @Scheduled(fixedRate = 900_000) // every 15 minutes
    public void evictStaleEntries() {
        Instant windowStart = Instant.now().minus(WINDOW);
        Iterator<Map.Entry<String, Deque<Instant>>> it = failedAttempts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Deque<Instant>> entry = it.next();
            Deque<Instant> timestamps = entry.getValue();
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }
            if (timestamps.isEmpty()) {
                it.remove();
            }
        }
    }
}
