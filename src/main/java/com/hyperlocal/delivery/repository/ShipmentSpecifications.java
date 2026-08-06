package com.hyperlocal.delivery.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import jakarta.persistence.criteria.JoinType;

import org.springframework.data.jpa.domain.Specification;

import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.ShipmentStatus;

/**
 * Reusable JPA {@link Specification} builders for dynamic shipment queries.
 */
public final class ShipmentSpecifications {

    private ShipmentSpecifications() {}

    public static Specification<Shipment> belongsToBusiness(Long businessId) {
        return (root, query, cb) -> cb.equal(root.get("business").get("id"), businessId);
    }

    public static Specification<Shipment> hasStatus(ShipmentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * Multi-value status filter (e.g. the register list's comma-joined
     * {@code status=delivered,failed} query param, parsed into an enum
     * list by the caller). An empty or null list matches everything.
     */
    public static Specification<Shipment> statusIn(List<ShipmentStatus> statuses) {
        return (root, query, cb) -> statuses == null || statuses.isEmpty()
                ? cb.conjunction()
                : root.get("status").in(statuses);
    }

    public static Specification<Shipment> assignedToAgent(Long agentId) {
        return (root, query, cb) -> cb.equal(root.get("assignedAgent").get("id"), agentId);
    }

    public static Specification<Shipment> createdAfter(LocalDate from) {
        LocalDateTime start = LocalDateTime.of(from, LocalTime.MIN);
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start);
    }

    public static Specification<Shipment> createdBefore(LocalDate to) {
        LocalDateTime end = LocalDateTime.of(to, LocalTime.MAX);
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), end);
    }

    /**
     * Left-join fetches the assigned agent alongside the shipment so callers
     * that map {@link Shipment} to a DTO outside the transactional boundary
     * (e.g. in a controller, with {@code open-in-view: false}) don't hit a
     * {@link org.hibernate.LazyInitializationException} touching
     * {@code assignedAgent}. Skipped on the {@code Specification}'s count
     * query (result type {@code Long}), since fetch joins are meaningless —
     * and, per JPA, disallowed — there.
     *
     * <p>{@link org.springframework.data.jpa.repository.EntityGraph} isn't an
     * option here: it only attaches to a declared repository method, not to
     * the ad-hoc {@code findAll(Specification, Pageable)} used by the
     * register search, so the fetch has to be expressed as part of the
     * specification itself instead.
     */
    public static Specification<Shipment> fetchAssignedAgent() {
        return (root, query, cb) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("assignedAgent", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }
}
