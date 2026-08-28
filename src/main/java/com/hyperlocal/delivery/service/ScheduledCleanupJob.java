package com.hyperlocal.delivery.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hyperlocal.delivery.model.OtpStatus;
import com.hyperlocal.delivery.repository.OtpRecordRepository;
import com.hyperlocal.delivery.repository.PendingRegistrationRepository;
import com.hyperlocal.delivery.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

/**
 * Periodic housekeeping job that purges expired refresh tokens, OTP records,
 * and pending registration payloads from the database. Token cleanup runs
 * daily at 03:00 by default; OTP cleanup runs every hour.
 *
 * <p><strong>Scaling note:</strong> These jobs are idempotent (DELETE
 * operations are safe to run multiple times). However, if the application
 * is scaled to multiple instances, each instance will fire the same job
 * concurrently — wasting database capacity. To prevent this, add
 * <a href="https://github.com/lukas-krecan/ShedLock">ShedLock</a> with
 * {@code @SchedulerLock} annotations when horizontal scaling is needed.
 */
@Component
@RequiredArgsConstructor
public class ScheduledCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledCleanupJob.class);
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpRecordRepository otpRecordRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;

    /**
     * Delete all refresh tokens whose {@code expires_at} is in the past.
     */
    @Scheduled(cron = "${app.scheduling.refresh-cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void purgeExpiredTokens() {
        long deletedRefresh = refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (deletedRefresh > 0) {
            log.info("Purged {} expired refresh token(s)", deletedRefresh);
        }
    }

    /**
     * Purge stale OTP records and expired pending registration payloads.
     * Deletes:
     * <ul>
     *   <li>OTP records with status VERIFIED</li>
     *   <li>OTP records with status INVALIDATED</li>
     *   <li>OTP records with status ACTIVE whose expiry has passed</li>
     *   <li>Pending registration payloads whose expiry has passed</li>
     * </ul>
     *
     * Runs every hour. Any exception is logged and swallowed so the next
     * scheduled cycle can retry without interrupting normal OTP operations.
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void purgeOtpRecords() {
        try {
            LocalDateTime now = LocalDateTime.now();

            long deletedVerified = otpRecordRepository.deleteByStatus(OtpStatus.VERIFIED);
            long deletedInvalidated = otpRecordRepository.deleteByStatus(OtpStatus.INVALIDATED);
            long deletedExpiredActive = otpRecordRepository.deleteExpiredRecords(now);
            long deletedPendingRegs = pendingRegistrationRepository.deleteByExpiresAtBefore(now);

            long totalDeleted = deletedVerified + deletedInvalidated + deletedExpiredActive + deletedPendingRegs;

            if (totalDeleted > 0) {
                log.info("OTP cleanup completed: {} verified, {} invalidated, {} expired active OTP record(s), "
                        + "{} expired pending registration(s) deleted",
                        deletedVerified, deletedInvalidated, deletedExpiredActive, deletedPendingRegs);
            }
        } catch (Exception ex) {
            log.error("OTP cleanup job failed; will retry on next cycle", ex);
        }
    }
}
