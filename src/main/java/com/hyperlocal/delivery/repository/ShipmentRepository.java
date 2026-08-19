package com.hyperlocal.delivery.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.hyperlocal.delivery.dto.analytics.AgentStats;
import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.ShipmentStatus;

/**
 * Data access for {@link Shipment} records. Tenant isolation is enforced by
 * callers via the {@code businessId} parameter.
 */
@Repository
public interface ShipmentRepository
        extends JpaRepository<Shipment, Long>, JpaSpecificationExecutor<Shipment> {

    /**
     * Load a shipment by id, constrained to the given tenant.
     */
    Optional<Shipment> findByIdAndBusiness_Id(Long id, Long businessId);

    /**
     * Public tracking lookup by opaque tracking token.
     */
    Optional<Shipment> findByTrackingToken(String token);

    /**
     * Paged listing of shipments assigned to a given agent.
     */
    Page<Shipment> findByAssignedAgent_Id(Long agentId, Pageable pageable);

    /**
     * Paged listing of shipments assigned to a given agent, filtered by
     * a set of statuses (e.g. the set of "active" statuses).
     */
    Page<Shipment> findByAssignedAgent_IdAndStatusIn(
            Long agentId, Collection<ShipmentStatus> statuses, Pageable pageable);

    /**
     * Count of active shipments for a given agent; used to gate agent
     * deactivation and to feed the auto-assignment load calculation.
     */
    long countByAssignedAgent_IdAndStatusIn(
            Long agentId, Collection<ShipmentStatus> statuses);

    /**
     * Eagerly fetches the shipment along with its event history (and each
     * event's {@code changedBy} user) and assigned agent in a single query,
     * avoiding N+1 loads on the detail endpoint. Deliberately does
     * <strong>not</strong> also fetch-join {@code attempts} here — see
     * {@link #findWithDetailById} for why the two collections must be
     * fetched in separate queries.
     *
     * <p>Not intended to be called directly by application code; callers
     * should use {@link #findWithDetailById} (or {@link
     * #findWithDetailByTrackingToken}), which layer the {@code attempts}
     * fetch on top of this query.
     */
    @EntityGraph(attributePaths = { "events", "events.changedBy", "assignedAgent" })
    @Query("SELECT s FROM Shipment s WHERE s.id = :id")
    Optional<Shipment> findDetailByIdWithoutAttempts(@Param("id") Long id);

    /**
     * Same as {@link #findDetailByIdWithoutAttempts}, keyed by the opaque
     * tracking token instead of the primary key.
     */
    @EntityGraph(attributePaths = { "events", "events.changedBy", "assignedAgent" })
    @Query("SELECT s FROM Shipment s WHERE s.trackingToken = :token")
    Optional<Shipment> findDetailByTrackingTokenWithoutAttempts(@Param("token") String token);

    /**
     * Fetch-joins just {@code attempts} (and each attempt's {@code agent}
     * user) for the shipment with the given id. Relies on first-level-cache
     * identity: run in the same persistence context as
     * {@link #findDetailByIdWithoutAttempts} / {@link
     * #findDetailByTrackingTokenWithoutAttempts}, this query returns the
     * <em>same managed {@link Shipment} instance</em> already loaded by
     * those queries, and populates its (until-now uninitialized)
     * {@code attempts} collection as a side effect — merging the two fetches
     * onto one entity without ever joining both collections in one SQL
     * statement.
     */
    @Query("SELECT DISTINCT s FROM Shipment s LEFT JOIN FETCH s.attempts a LEFT JOIN FETCH a.agent WHERE s.id = :id")
    Optional<Shipment> fetchAttemptsById(@Param("id") Long id);

    /**
     * Eagerly fetches the shipment along with its event history (and each
     * event's {@code changedBy} user), delivery attempts (and each
     * attempt's {@code agent} user), and assigned agent — avoiding N+1
     * loads on the detail endpoint. The nested {@code events.changedBy} /
     * {@code attempts.agent} paths are required, not just an optimization:
     * {@link com.hyperlocal.delivery.dto.shipment.ShipmentEventDto#from}
     * and {@link com.hyperlocal.delivery.dto.shipment.DeliveryAttemptDto#from}
     * both touch those associations, and callers that map the returned
     * entity to a DTO outside the transactional boundary (open-in-view is
     * disabled) would otherwise hit a {@code LazyInitializationException}.
     *
     * <p>Deliberately issues <strong>two</strong> queries (via {@link
     * #findDetailByIdWithoutAttempts} then {@link #fetchAttemptsById})
     * rather than fetch-joining {@code events} and {@code attempts} in one
     * query: {@code events} is a {@code List} (a Hibernate "bag", not
     * indexed) and {@code attempts} is a {@code Set}, and fetch-joining two
     * collections in a single query produces a cartesian product of rows
     * (events x attempts). Hibernate can deduplicate the bag-mapped
     * collection, but a plain (unindexed) list is not deduplicated the way
     * a {@code Set} is, so {@code events} would end up with each row
     * repeated once per attempt. Splitting into two queries keeps each
     * collection's row count independent, at the cost of one extra
     * (indexed, cheap) query — still far better than the N+1 this
     * {@code @EntityGraph} was originally introduced to avoid.
     */
    default Optional<Shipment> findWithDetailById(Long id) {
        Optional<Shipment> shipment = findDetailByIdWithoutAttempts(id);
        shipment.ifPresent(s -> fetchAttemptsById(s.getId()));
        return shipment;
    }

    /**
     * Same eager fetch as {@link #findWithDetailById}, keyed by the opaque
     * tracking token instead of the primary key — used by the register
     * inspect endpoint. See {@link #findWithDetailById} for why this is
     * split into two queries instead of one combined fetch-join.
     */
    default Optional<Shipment> findWithDetailByTrackingToken(String token) {
        Optional<Shipment> shipment = findDetailByTrackingTokenWithoutAttempts(token);
        shipment.ifPresent(s -> fetchAttemptsById(s.getId()));
        return shipment;
    }

    /**
     * Acquires a {@code PESSIMISTIC_WRITE} row lock on the shipment with the
     * given id. Intended to serialize concurrent status-transition paths
     * (agent single-step advances and delivery-attempt recording) so two
     * concurrent transitions reading the same starting status can't both
     * commit and leave a contradictory terminal state.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shipment s WHERE s.id = :id")
    Optional<Shipment> lockById(@Param("id") Long id);

    // ─── Analytics queries ───────────────────────────────────────────────

    /**
     * Per-agent performance stats via JPQL constructor expression.
     */
    @Query("""
            SELECT new com.hyperlocal.delivery.dto.analytics.AgentStats(
                u.id, u.fullName,
                COUNT(s),
                SUM(CASE WHEN s.status = com.hyperlocal.delivery.model.ShipmentStatus.DELIVERED THEN 1L ELSE 0L END),
                SUM(CASE WHEN s.status = com.hyperlocal.delivery.model.ShipmentStatus.FAILED THEN 1L ELSE 0L END),
                SUM(CASE WHEN s.status = com.hyperlocal.delivery.model.ShipmentStatus.RETURNED THEN 1L ELSE 0L END),
                CAST(COALESCE(AVG(CAST(FUNCTION('TIMESTAMPDIFF', HOUR, s.createdAt, s.deliveredAt) AS Double)), 0.0) AS Double),
                u.isActive
            )
            FROM User u
            LEFT JOIN Shipment s
              ON s.assignedAgent = u
             AND s.createdAt BETWEEN :from AND :to
            WHERE u.business.id = :businessId
              AND u.role = com.hyperlocal.delivery.model.UserRole.DELIVERY_AGENT
              AND u.deletedAt IS NULL
            GROUP BY u.id, u.fullName, u.isActive
            ORDER BY COUNT(s) DESC
            """)
    List<AgentStats> agentStats(@Param("businessId") Long businessId,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    /**
     * Daily shipment volume breakdown (native query — MySQL DATE function).
     */
    @Query(value = """
            SELECT DATE(s.created_at) as date,
                   COUNT(*) as total,
                   SUM(CASE WHEN s.status = 'DELIVERED' THEN 1 ELSE 0 END) as delivered,
                   SUM(CASE WHEN s.status = 'FAILED' THEN 1 ELSE 0 END) as failed,
                   SUM(CASE WHEN s.status NOT IN ('DELIVERED','FAILED','RETURNED') THEN 1 ELSE 0 END) as inProgress
            FROM shipments s
            WHERE s.business_id = :businessId
              AND s.created_at BETWEEN :from AND :to
            GROUP BY DATE(s.created_at)
            ORDER BY DATE(s.created_at) ASC
            """, nativeQuery = true)
    List<Object[]> dailyVolumeRaw(@Param("businessId") Long businessId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    /**
     * Aggregate overview counts for a business within a date range (native query).
     */
    @Query(value = """
            SELECT COUNT(*) as total,
                   SUM(CASE WHEN s.status = 'DELIVERED' THEN 1 ELSE 0 END) as delivered,
                   SUM(CASE WHEN s.status = 'FAILED' THEN 1 ELSE 0 END) as failed,
                   SUM(CASE WHEN s.status = 'RETURNED' THEN 1 ELSE 0 END) as returned,
                   SUM(CASE WHEN s.status NOT IN ('DELIVERED','FAILED','RETURNED') THEN 1 ELSE 0 END) as inProgress,
                   AVG(TIMESTAMPDIFF(HOUR, s.created_at, s.delivered_at)) as avgHours
            FROM shipments s
            WHERE s.business_id = :businessId
              AND s.created_at BETWEEN :from AND :to
            """, nativeQuery = true)
    List<Object[]> overviewRaw(@Param("businessId") Long businessId,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to);

    /**
     * On-time delivery rate: count of shipments delivered before or at
     * their scheduled time vs total delivered.
     */
    @Query(value = """
            SELECT COUNT(CASE WHEN s.delivered_at <= s.scheduled_delivery_at THEN 1 END) as onTime,
                   COUNT(CASE WHEN s.status = 'DELIVERED' THEN 1 END) as totalDelivered
            FROM shipments s
            WHERE s.business_id = :businessId
              AND s.created_at BETWEEN :from AND :to
              AND s.status = 'DELIVERED'
            """, nativeQuery = true)
    List<Object[]> onTimeRateRaw(@Param("businessId") Long businessId,
                           @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to);

    /**
     * First-attempt success rate: shipments delivered without any failed
     * delivery attempt recorded.
     *
     * <p>Uses a {@code NOT EXISTS} correlated subquery rather than a
     * {@code LEFT JOIN delivery_attempts} — a shipment with N prior failed
     * attempts joins to N rows, so {@code COUNT(*)} over the join fans out
     * per-attempt instead of counting shipments once each, silently
     * inflating {@code totalDelivered} (and understating the rate) for
     * every delivered shipment with 2+ recorded attempts. Confirmed live:
     * one shipment delivered clean plus one with 1 prior attempt plus one
     * with 2 prior attempts produced {@code COUNT(*) = 4} instead of the
     * correct 3 — see docs/audits/2026-09-10-flow-reports-analytics.md.
     */
    @Query(value = """
            SELECT COUNT(*) as totalDelivered,
                   SUM(CASE WHEN NOT EXISTS (
                       SELECT 1 FROM delivery_attempts da WHERE da.shipment_id = s.id
                   ) THEN 1 ELSE 0 END) as firstAttemptSuccess
            FROM shipments s
            WHERE s.business_id = :businessId
              AND s.created_at BETWEEN :from AND :to
              AND s.status = 'DELIVERED'
            """, nativeQuery = true)
    List<Object[]> firstAttemptSuccessRaw(@Param("businessId") Long businessId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    /**
     * Count of failed-delivery attempts grouped by {@code failure_reason},
     * scoped to shipments created within the given date range. Reasons with
     * zero occurrences are simply absent from the result — callers should
     * zero-fill against {@code FailureReason.values()}.
     */
    @Query(value = """
            SELECT da.failure_reason as reason, COUNT(*) as cnt
            FROM delivery_attempts da
            JOIN shipments s ON s.id = da.shipment_id
            WHERE s.business_id = :businessId
              AND s.created_at BETWEEN :from AND :to
            GROUP BY da.failure_reason
            """, nativeQuery = true)
    List<Object[]> failureReasonBreakdownRaw(@Param("businessId") Long businessId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);
}
