package com.hyperlocal.delivery.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hyperlocal.delivery.model.PendingRegistration;

/**
 * Data access for {@link PendingRegistration} entities. Provides lookup by
 * email and cleanup of expired registration payloads.
 */
@Repository
public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    /**
     * Find a pending registration by email address.
     */
    Optional<PendingRegistration> findByEmail(String email);

    /**
     * Delete all pending registrations that have expired before the given
     * cutoff time. Invoked by the scheduled cleanup job.
     *
     * @return number of rows deleted
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PendingRegistration p WHERE p.expiresAt < :cutoff")
    long deleteByExpiresAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
