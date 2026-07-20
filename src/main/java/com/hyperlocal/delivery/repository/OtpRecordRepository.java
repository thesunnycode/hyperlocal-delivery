package com.hyperlocal.delivery.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hyperlocal.delivery.model.OtpPurpose;
import com.hyperlocal.delivery.model.OtpRecord;
import com.hyperlocal.delivery.model.OtpStatus;

/**
 * Data access for {@link OtpRecord} entities. Provides lookup of the current
 * active OTP for a given email+purpose and bulk-deletion helpers used by the
 * scheduled cleanup job.
 */
@Repository
public interface OtpRecordRepository extends JpaRepository<OtpRecord, Long> {

    /**
     * Find the currently active OTP record for a given email and purpose.
     *
     * @return the active record, or empty if none exists
     */
    @Query("SELECT o FROM OtpRecord o WHERE o.email = :email AND o.purpose = :purpose AND o.status = 'ACTIVE'")
    Optional<OtpRecord> findActiveByEmailAndPurpose(
            @Param("email") String email,
            @Param("purpose") OtpPurpose purpose);

    /**
     * Delete all OTP records that have expired before the given cutoff time.
     * Invoked by the scheduled cleanup job.
     *
     * @return number of rows deleted
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM OtpRecord o WHERE o.expiresAt < :cutoff")
    long deleteExpiredRecords(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Invalidate all ACTIVE OTP records for the given email and purpose by
     * setting their status to INVALIDATED. Used when generating a new OTP
     * to ensure only one active record exists per email+purpose.
     *
     * @return number of rows updated
     */
    @Modifying
    @Transactional
    @Query("UPDATE OtpRecord o SET o.status = 'INVALIDATED' "
            + "WHERE o.email = :email AND o.purpose = :purpose AND o.status = 'ACTIVE'")
    int invalidateActiveRecords(
            @Param("email") String email,
            @Param("purpose") OtpPurpose purpose);

    /**
     * Delete all OTP records with the given status. Used to purge VERIFIED
     * and INVALIDATED records during cleanup.
     *
     * @return number of rows deleted
     */
    @Modifying
    @Transactional
    long deleteByStatus(OtpStatus status);
}
