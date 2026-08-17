package com.hyperlocal.delivery.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.hyperlocal.delivery.model.AgentInvite;

/**
 * Invite tokens are looked up by hash, never by plaintext — the raw token is
 * never stored.
 */
@Repository
public interface AgentInviteRepository extends JpaRepository<AgentInvite, Long> {

    Optional<AgentInvite> findByTokenHash(String tokenHash);

    /**
     * Spend an invite, but only if it is still unspent and unexpired.
     *
     * <p>Written as a conditional UPDATE rather than a read-then-write so two
     * requests racing on the same link cannot both pass the check: the
     * database decides, and the loser gets 0 rows.
     */
    @Modifying
    @Query("""
            UPDATE AgentInvite i
               SET i.acceptedAt = :now
             WHERE i.tokenHash = :tokenHash
               AND i.acceptedAt IS NULL
               AND i.expiresAt > :now
            """)
    int markAccepted(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    /**
     * Retire any outstanding invites for an agent. Issuing a new link should
     * invalidate the previous one, otherwise every link ever sent stays live
     * until it expires on its own.
     */
    @Modifying
    @Query("""
            UPDATE AgentInvite i
               SET i.expiresAt = :now
             WHERE i.user.id = :userId
               AND i.acceptedAt IS NULL
               AND i.expiresAt > :now
            """)
    int expireOutstanding(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** Housekeeping: drop rows that can no longer be used. */
    @Modifying
    @Query("DELETE FROM AgentInvite i WHERE i.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
