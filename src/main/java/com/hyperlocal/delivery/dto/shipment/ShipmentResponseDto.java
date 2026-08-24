package com.hyperlocal.delivery.dto.shipment;

import java.util.List;

import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.ShipmentStatus;

/**
 * Full shipment detail response including events and delivery attempts.
 *
 * <p>Field names match the real frontend contract directly (renamed, not
 * aliased, per the response-side precedent used elsewhere in this plan):
 * OwnerShipmentsPage.jsx reads {@code selected.token}, {@code selected.address},
 * {@code selected.scheduledAt}, {@code selected.agentName}, and
 * {@code selected.agentId} — not {@code trackingToken}, {@code deliveryAddress},
 * {@code scheduledDeliveryAt}, or a nested {@code assignedAgent} object.
 */
public record ShipmentResponseDto(
        Long id,
        String token,
        ShipmentStatus status,
        String customerName,
        String customerPhone,
        String address,
        String scheduledAt,
        String deliveredAt,
        Long agentId,
        String agentName,
        List<ShipmentEventDto> events,
        List<DeliveryAttemptDto> attempts,
        String createdAt,
        String updatedAt
) {

    /**
     * Build from a shipment entity with eagerly loaded associations.
     */
    public static ShipmentResponseDto from(Shipment s) {
        List<ShipmentEventDto> eventDtos = s.getEvents() != null
                ? s.getEvents().stream().map(ShipmentEventDto::from).toList()
                : List.of();

        List<DeliveryAttemptDto> attemptDtos = s.getAttempts() != null
                ? s.getAttempts().stream().map(DeliveryAttemptDto::from).toList()
                : List.of();

        AgentMiniDto agent = AgentMiniDto.from(s.getAssignedAgent());

        return new ShipmentResponseDto(
                s.getId(),
                s.getTrackingToken(),
                s.getStatus(),
                s.getCustomerName(),
                s.getCustomerPhone(),
                s.getDeliveryAddress(),
                s.getScheduledDeliveryAt() != null ? s.getScheduledDeliveryAt().toString() : null,
                s.getDeliveredAt() != null ? s.getDeliveredAt().toString() : null,
                agent != null ? agent.id() : null,
                agent != null ? agent.fullName() : null,
                eventDtos,
                attemptDtos,
                s.getCreatedAt() != null ? s.getCreatedAt().toString() : null,
                s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null
        );
    }
}
