package com.hyperlocal.delivery.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hyperlocal.delivery.model.ShipmentEvent;

/**
 * Data access for the append-only {@link ShipmentEvent} history.
 */
@Repository
public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, Long> {

    /**
     * Returns the full status-transition history of a shipment ordered
     * by insertion time ascending (oldest first).
     */
    List<ShipmentEvent> findByShipment_IdOrderByCreatedAtAsc(Long shipmentId);
}
