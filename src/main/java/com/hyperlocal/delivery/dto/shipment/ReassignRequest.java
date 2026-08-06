package com.hyperlocal.delivery.dto.shipment;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

/**
 * Request body for the owner's reassign action. Available on any
 * non-terminal shipment: changes the assigned agent, and additionally
 * transitions FAILED shipments back to ASSIGNED. If {@code agentId} is
 * null, auto-assignment (least-loaded active agent) is used.
 */
public record ReassignRequest(
        Long agentId,
        @JsonAlias("note") @Size(max = 500) String notes
) {}
