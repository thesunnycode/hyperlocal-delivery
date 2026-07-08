package com.hyperlocal.delivery.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hyperlocal.delivery.model.Business;

/**
 * Data access for {@link Business} tenant roots. Queries exclude
 * soft-deleted rows by filtering on {@code deleted_at IS NULL}.
 */
@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {

    /**
     * Look up an active (non soft-deleted) business by its login email.
     */
    Optional<Business> findByEmailAndDeletedAtIsNull(String email);

    /**
     * Existence check used to enforce the unique-email constraint at
     * registration time (includes soft-deleted rows to keep historical
     * emails reserved).
     */
    boolean existsByEmail(String email);
}
