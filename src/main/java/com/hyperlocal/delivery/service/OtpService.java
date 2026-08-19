package com.hyperlocal.delivery.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hyperlocal.delivery.exception.ValidationException;
import com.hyperlocal.delivery.model.OtpPurpose;
import com.hyperlocal.delivery.model.OtpRecord;
import com.hyperlocal.delivery.model.OtpStatus;
import com.hyperlocal.delivery.repository.OtpRecordRepository;
import com.hyperlocal.delivery.security.JwtUtil;

import lombok.RequiredArgsConstructor;

/**
 * Core service handling OTP lifecycle: generation, hashing, storage, and
 * validation. Generation produces a cryptographically random 6-digit code,
 * stores only its SHA-256 hash, and returns the plaintext for email delivery.
 * Validation uses constant-time hash comparison and enforces check ordering
 * (status → expiry → attempts → code correctness).
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    /** OTP codes are valid for 5 minutes from generation. */
    static final int OTP_EXPIRY_MINUTES = 5;

    /** Maximum allowed verification attempts before permanent invalidation. */
    static final int MAX_ATTEMPTS = 5;

    /** Lower bound (inclusive) of the 6-digit range. */
    private static final int OTP_MIN = 100_000;

    /** Number of values in the 6-digit range [100000, 999999]. */
    private static final int OTP_RANGE = 900_000;

    /**
     * RFC 5322 simplified email regex. Permits standard local@domain format
     * while rejecting obviously malformed addresses (no @, spaces, empty
     * local part, etc.).
     */
    private static final String EMAIL_REGEX =
            "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?"
                    + "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?)*$";

    private final OtpRecordRepository otpRecordRepository;
    private final OtpRateLimiter otpRateLimiter;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate an OTP for the given email and purpose.
     *
     * <p>Steps:
     * <ol>
     *   <li>Validate email format (RFC 5322)</li>
     *   <li>Validate purpose is a known enum value</li>
     *   <li>Check rate limiter</li>
     *   <li>Invalidate all prior ACTIVE records for same email+purpose</li>
     *   <li>Generate a 6-digit code using SecureRandom</li>
     *   <li>Hash with SHA-256 and persist an OtpRecord</li>
     *   <li>Return plaintext code for email delivery</li>
     * </ol>
     *
     * @param email   the target email address
     * @param purpose the OTP purpose (REGISTRATION or PASSWORD_RESET)
     * @return the plaintext 6-digit code (for email delivery only, never persisted)
     * @throws ValidationException if email is malformed or purpose is null
     * @throws com.hyperlocal.delivery.exception.RateLimitExceededException if rate limit is exceeded
     */
    @Transactional
    public String generateOtp(String email, OtpPurpose purpose) {
        // 1. Validate email format
        validateEmail(email);

        // 2. Validate purpose
        validatePurpose(purpose);

        // Normalize email for storage and lookups
        String normalizedEmail = email.trim().toLowerCase();

        // 3. Check rate limit
        otpRateLimiter.checkRateLimit(normalizedEmail, purpose);

        // 4. Invalidate all prior ACTIVE records for same email+purpose
        otpRecordRepository.invalidateActiveRecords(normalizedEmail, purpose);

        // 5. Generate 6-digit code
        int code = OTP_MIN + secureRandom.nextInt(OTP_RANGE);
        String plaintext = String.valueOf(code);

        // 6. Hash the code with SHA-256
        String codeHash = JwtUtil.sha256Hex(plaintext);

        // 7. Build and save the OTP record
        LocalDateTime now = LocalDateTime.now();
        OtpRecord record = OtpRecord.builder()
                .email(normalizedEmail)
                .purpose(purpose)
                .codeHash(codeHash)
                .status(OtpStatus.ACTIVE)
                .attemptCount(0)
                .expiresAt(now.plusMinutes(OTP_EXPIRY_MINUTES))
                .build();
        otpRecordRepository.save(record);

        log.info("OTP generated for email={} purpose={}", normalizedEmail, purpose);

        // 8. Return plaintext for caller to send via email
        return plaintext;
    }

    /**
     * Validate an OTP submission. Checks in order: record existence, status,
     * expiry, attempts, code match.
     *
     * <p>Check ordering guarantees deterministic failure reasons:
     * <ol>
     *   <li>Record existence (no active record → NO_RECORD)</li>
     *   <li>Status (VERIFIED/INVALIDATED → NO_RECORD, defensive)</li>
     *   <li>Expiry (past expires_at → EXPIRED, no counter increment)</li>
     *   <li>Attempt count (≥5 → MAX_ATTEMPTS, defensive)</li>
     *   <li>Code correctness (constant-time comparison)</li>
     * </ol>
     *
     * @param email   the email address
     * @param code    the 6-digit OTP code submitted by the user
     * @param purpose the OTP purpose
     * @return typed result indicating success or failure with details
     *
     * <p>Runs in its own transaction ({@code REQUIRES_NEW}), not the
     * caller's. Callers such as {@code AuthService.verifyRegistrationOtp}
     * throw a rejection exception in the same method right after a failed
     * validation — if this ran in that same transaction, the default
     * rollback-on-exception would undo the attempt-count increment this
     * method just committed, and the 5-wrong-attempts lockout would never
     * actually trip. Committing independently means a recorded attempt
     * stays recorded no matter what the caller does afterward.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OtpValidationResult validateOtp(String email, String code, OtpPurpose purpose) {
        // 1. Normalize email
        String normalizedEmail = email.trim().toLowerCase();

        // 2. Look up active record
        var optionalRecord = otpRecordRepository.findActiveByEmailAndPurpose(normalizedEmail, purpose);
        if (optionalRecord.isEmpty()) {
            log.warn("OTP validation failed: email={}, purpose={}, reason=NO_RECORD", normalizedEmail, purpose);
            return new OtpValidationResult.Failure(OtpValidationResult.FailureReason.NO_RECORD, null);
        }

        OtpRecord record = optionalRecord.get();

        // 3. Defensive status check (shouldn't happen with ACTIVE query, but defensive)
        if (record.getStatus() != OtpStatus.ACTIVE) {
            log.warn("OTP validation failed: email={}, purpose={}, reason=NO_RECORD (non-active status)", normalizedEmail, purpose);
            return new OtpValidationResult.Failure(OtpValidationResult.FailureReason.NO_RECORD, null);
        }

        // 4. Check expiry — DO NOT increment counter
        if (LocalDateTime.now().isAfter(record.getExpiresAt())) {
            log.warn("OTP validation failed: email={}, purpose={}, reason=EXPIRED", normalizedEmail, purpose);
            return new OtpValidationResult.Failure(OtpValidationResult.FailureReason.EXPIRED, null);
        }

        // 5. Check attempt count (defensive — should be INVALIDATED already at 5)
        if (record.getAttemptCount() >= MAX_ATTEMPTS) {
            log.warn("OTP validation failed: email={}, purpose={}, reason=MAX_ATTEMPTS", normalizedEmail, purpose);
            return new OtpValidationResult.Failure(OtpValidationResult.FailureReason.MAX_ATTEMPTS, null);
        }

        // 6. Check code correctness with constant-time comparison
        String submittedHash = JwtUtil.sha256Hex(code);
        boolean codeMatches = MessageDigest.isEqual(
                submittedHash.getBytes(StandardCharsets.UTF_8),
                record.getCodeHash().getBytes(StandardCharsets.UTF_8));

        if (codeMatches) {
            // Success: mark record VERIFIED
            record.setStatus(OtpStatus.VERIFIED);
            otpRecordRepository.save(record);
            log.info("OTP validated successfully: email={}, purpose={}", normalizedEmail, purpose);
            return new OtpValidationResult.Success();
        }

        // Wrong code: increment attempt count
        int newAttemptCount = record.getAttemptCount() + 1;
        record.setAttemptCount(newAttemptCount);

        if (newAttemptCount >= MAX_ATTEMPTS) {
            // 5th failed attempt: invalidate permanently
            record.setStatus(OtpStatus.INVALIDATED);
            otpRecordRepository.save(record);
            log.warn("OTP validation failed: email={}, purpose={}, reason=MAX_ATTEMPTS (just reached)", normalizedEmail, purpose);
            return new OtpValidationResult.Failure(OtpValidationResult.FailureReason.MAX_ATTEMPTS, 0);
        }

        otpRecordRepository.save(record);
        int remaining = MAX_ATTEMPTS - newAttemptCount;
        log.warn("OTP validation failed: email={}, purpose={}, reason=INVALID_CODE, remaining={}", normalizedEmail, purpose, remaining);
        return new OtpValidationResult.Failure(OtpValidationResult.FailureReason.INVALID_CODE, remaining);
    }

    // ─────────────────────────── helpers ───────────────────────────

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ValidationException("Email address is required");
        }
        if (!email.trim().matches(EMAIL_REGEX)) {
            throw new ValidationException("Email address is not well-formed");
        }
    }

    private void validatePurpose(OtpPurpose purpose) {
        if (purpose == null) {
            throw new ValidationException("OTP purpose is required and must be a known value");
        }
        // Enum type safety guarantees only known values reach here; this
        // null check handles the case where the caller passes null explicitly.
    }
}
