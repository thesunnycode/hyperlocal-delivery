package com.hyperlocal.delivery.dto.shipment;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

/**
 * Request body for the owner's cancel action. Available on any non-terminal
 * shipment (same set as {@link ReassignRequest}); once cancelled, a
 * shipment can never be reassigned, delivered, or otherwise mutated.
 */
public record CancelShipmentRequest(
        @JsonAlias("note") @Size(max = 500) String notes
) {}
