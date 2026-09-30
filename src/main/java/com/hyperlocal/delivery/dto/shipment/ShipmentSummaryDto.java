package com.hyperlocal.delivery.dto.shipment;

import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.util.TimeUtils;

/**
 * Lightweight shipment summary for paginated list endpoints.
 *
 * <p>Field names match the real frontend contract directly (same rename as
 * {@link ShipmentResponseDto}): the owner shipments page
 * ({@code /owner/shipments}) list rows read
 * {@code s.token}, {@code s.scheduledAt}, and {@code s.agentName} — not
 * {@code trackingToken}, {@code scheduledDeliveryAt}, or a nested
 * {@code assignedAgent} object.
 */
public record ShipmentSummaryDto(
        Long id,
        String token,
        ShipmentStatus status,
        String customerName,
        String address,
        Long agentId,
        String agentName,
        String scheduledAt,
        String createdAt,
        /** Null unless {@code status} is DELIVERED. Added so a summary list
         *  — e.g. the agent's own {@code /shipments/mine?status=delivered} —
         *  can answer "how many delivered in the last N days" without a
         *  second, per-record fetch of the full {@link ShipmentResponseDto}. */
        String deliveredAt
) {

    /**
     * Build from a shipment entity.
     */
    public static ShipmentSummaryDto from(Shipment s) {
        AgentMiniDto agent = AgentMiniDto.from(s.getAssignedAgent());
        return new ShipmentSummaryDto(
                s.getId(),
                s.getTrackingToken(),
                s.getStatus(),
                s.getCustomerName(),
                s.getDeliveryAddress(),
                agent != null ? agent.id() : null,
                agent != null ? agent.fullName() : null,
                TimeUtils.toIso(s.getScheduledDeliveryAt()),
                TimeUtils.toIso(s.getCreatedAt()),
                TimeUtils.toIso(s.getDeliveredAt())
        );
    }
}
