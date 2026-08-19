package com.hyperlocal.delivery.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hyperlocal.delivery.model.RefreshToken;

/**
 * Data access for persisted refresh-token hashes. The plaintext token is
 * never stored; only its SHA-256 hash lives in {@code token_hash}.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Look up a stored token by its SHA-256 hash.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Delete a specific token by hash. Used to enforce single-use rotation.
     *
     * <p>A hand-written bulk {@code DELETE}, not the derived
     * {@code deleteByTokenHash} Spring Data would otherwise generate. The
     * derived form loads the matching entity first and then removes it via
     * the persistence context, which — same as {@code delete(entity)} —
     * asserts the row was still there to remove and throws
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * at commit if a concurrent request already deleted it. A real bulk
     * {@code DELETE} statement has no such expectation: it simply reports
     * how many rows it removed, so a caller that lost a race to redeem the
     * same token sees a plain {@code 0} to handle in application code
     * instead of an exception surfacing at commit time.
     *
     * @return number of rows actually deleted (0 or 1)
     */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.tokenHash = :tokenHash")
    @Transactional
    long deleteByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Delete every token belonging to a user. Used during logout-all,
     * password reset (revokes sessions on every device), and account
     * deactivation.
     *
     * <p>A hand-written bulk {@code DELETE}, same reasoning as
     * {@link #deleteByTokenHash}: the derived form Spring Data would
     * otherwise generate for {@code deleteByUser_Id} loads each matching
     * entity and removes it individually, which throws
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * at commit if a concurrent caller (a racing logout, password reset, or
     * refresh) already deleted one of the same rows — surfacing a
     * concurrent, otherwise-harmless "these tokens are already gone" as a
     * raw 500. Unlike {@code deleteByTokenHash}, callers here don't need to
     * inspect the returned count — deleting zero rows on this path is not
     * an error, it just needs to stop throwing.
     *
     * @return number of rows deleted
     */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.user.id = :userId")
    @Transactional
    long deleteByUser_Id(@Param("userId") Long userId);

    /**
     * Delete every token that expired before the given cutoff. Invoked by
     * the scheduled cleanup job.
     *
     * @return number of rows deleted
     */
    @Modifying
    @Transactional
    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
