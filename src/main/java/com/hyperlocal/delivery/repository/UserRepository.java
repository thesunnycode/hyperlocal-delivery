package com.hyperlocal.delivery.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.model.UserRole;

/**
 * Data access for {@link User} records. Queries are tenant-scoped via
 * {@code business.id} and exclude soft-deleted rows.
 */
@Repository
public interface UserRepository
        extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /**
     * Look up an active user by login email across all tenants.
     */
    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    /**
     * Existence check used to enforce the unique-email constraint at
     * user creation time.
     */
    boolean existsByEmail(String email);

    /**
     * Load a user by id, constrained to the given tenant and excluding
     * soft-deleted rows.
     */
    Optional<User> findByIdAndBusiness_IdAndDeletedAtIsNull(Long id, Long businessId);

    /**
     * Load a user by id, constrained to the given tenant, including
     * soft-deleted rows. Used by the reactivate path, which must find an
     * agent that was previously soft-deleted.
     */
    Optional<User> findByIdAndBusiness_Id(Long id, Long businessId);

    /**
     * Paged listing of users with a specific role within a tenant,
     * excluding soft-deleted rows. Retained for non-agent callers; the
     * agent list endpoint uses {@link #findByBusiness_IdAndRole} instead
     * so deactivated agents (which are soft-deleted as part of
     * deactivation) are not permanently unreachable.
     */
    Page<User> findByBusiness_IdAndRoleAndDeletedAtIsNull(
            Long businessId, UserRole role, Pageable pageable);

    /**
     * Paged listing of every user with a specific role within a tenant,
     * regardless of active/deactivated (soft-deleted) state. Used for the
     * "All" view of the agent list so a deactivated agent remains
     * discoverable (and reactivatable) instead of disappearing from every
     * list once {@code deactivate()} sets {@code deletedAt}.
     */
    Page<User> findByBusiness_IdAndRole(
            Long businessId, UserRole role, Pageable pageable);

    /**
     * Paged listing of users with a specific role and active flag within
     * a tenant. Filters on {@code isActive} alone (not also
     * {@code deletedAt IS NULL}): deactivation always sets both fields
     * together and reactivation always clears both together, so the two
     * fields never disagree for agents, but requiring
     * {@code deletedAt IS NULL} in addition to {@code isActive = false}
     * would make the {@code active=false} filter unsatisfiable.
     */
    Page<User> findByBusiness_IdAndRoleAndIsActive(
            Long businessId, UserRole role, Boolean isActive, Pageable pageable);

    /**
     * Returns delivery agents for the given tenant ordered by current load
     * (count of shipments in {@code activeStatuses}) ascending, then by id
     * ascending as a stable tiebreaker. Used by the auto-assignment path
     * to pick the least-loaded agent; callers typically pass a
     * {@code Pageable} of size 1.
     */
    @Query("""
            SELECT u
            FROM User u
            LEFT JOIN Shipment s
              ON s.assignedAgent = u
             AND s.status IN :activeStatuses
            WHERE u.business.id = :businessId
              AND u.role = com.hyperlocal.delivery.model.UserRole.DELIVERY_AGENT
              AND u.isActive = true
              AND u.deletedAt IS NULL
            GROUP BY u
            ORDER BY COUNT(s) ASC, u.id ASC
            """)
    List<User> findAgentsByLoadAsc(
            @Param("businessId") Long businessId,
            @Param("activeStatuses") Collection<ShipmentStatus> activeStatuses,
            Pageable pageable);

    /**
     * Acquires a {@code PESSIMISTIC_WRITE} row lock on the user with the
     * given id. Intended to serialize concurrent deactivation and
     * auto-assignment paths.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> lockById(@Param("id") Long id);
}
