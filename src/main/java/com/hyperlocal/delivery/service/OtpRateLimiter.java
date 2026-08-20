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
import com.hyperlocal.delivery.model.OtpPurpose;

/**
 * In-memory sliding-window rate limiter for OTP generation requests.
 *
 * <p>Allows a maximum of {@value #MAX_REQUESTS} requests per email+purpose
 * combination within a {@value #WINDOW_MINUTES}-minute window. Rejected
 * requests do NOT consume a slot in the counter.
 *
 * <p>Suitable for single-instance deployments — state is lost on restart.
 */
@Component
public class OtpRateLimiter {

    static final int MAX_REQUESTS = 5;
    static final int WINDOW_MINUTES = 15;
    private static final Duration WINDOW = Duration.ofMinutes(WINDOW_MINUTES);

    private final ConcurrentHashMap<String, Deque<Instant>> requestLog = new ConcurrentHashMap<>();

    /**
     * Check whether a new OTP generation request is allowed for the given
     * email and purpose. If allowed, records the request timestamp.
     *
     * @throws RateLimitExceededException if the limit is exceeded, with
     *         the number of seconds until the next request is permitted
     */
    public void checkRateLimit(String email, OtpPurpose purpose) {
        String key = normalizeKey(email, purpose);
        Deque<Instant> timestamps = requestLog.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        Instant now = Instant.now();
        Instant windowStart = now.minus(WINDOW);

        // Evict entries outside the sliding window
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= MAX_REQUESTS) {
            // Compute retry-after from the oldest entry in the window
            Instant oldest = timestamps.peekFirst();
            long retryAfter = Duration.between(now, oldest.plus(WINDOW)).getSeconds();
            if (retryAfter <= 0) {
                retryAfter = 1;
            }
            throw new RateLimitExceededException(retryAfter);
        }

        // Record this request
        timestamps.addLast(now);
    }

    private String normalizeKey(String email, OtpPurpose purpose) {
        return email.trim().toLowerCase() + ":" + purpose.name();
    }

    /**
     * Periodic cleanup of stale entries whose timestamp deques are empty
     * or fully outside the sliding window. Prevents unbounded map growth
     * for one-shot registration emails that never return.
     */
    @Scheduled(fixedRate = 900_000) // every 15 minutes
    public void evictStaleEntries() {
        Instant windowStart = Instant.now().minus(WINDOW);
        Iterator<Map.Entry<String, Deque<Instant>>> it = requestLog.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Deque<Instant>> entry = it.next();
            Deque<Instant> timestamps = entry.getValue();
            // Evict expired entries from this deque
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }
            // Remove the key entirely if no entries remain
            if (timestamps.isEmpty()) {
                it.remove();
            }
        }
    }
}
