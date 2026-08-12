package com.hyperlocal.delivery.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hyperlocal.delivery.model.DeliveryAttempt;

/**
 * Data access for the append-only {@link DeliveryAttempt} log.
 */
@Repository
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {

    /**
     * Returns all failed-delivery attempts for a shipment ordered by
     * attempt time ascending (oldest first).
     */
    List<DeliveryAttempt> findByShipment_IdOrderByAttemptedAtAsc(Long shipmentId);

    /**
     * Count of recorded attempts for a shipment; used to derive the
     * next {@code attemptNumber}.
     */
    long countByShipment_Id(Long shipmentId);
}
