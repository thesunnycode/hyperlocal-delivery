package com.hyperlocal.delivery.dto.reports;

import java.util.List;

import com.hyperlocal.delivery.dto.shipment.DeliveryAttemptDto;
import com.hyperlocal.delivery.dto.shipment.ShipmentEventDto;
import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.util.TimeUtils;

/**
 * Single-shipment inspect view for the admin register page's detail panel
 * (task 14 brief), which reads {@code
 * inspect.customerName/address/status/createdAt/scheduledAt/deliveredAt/
 * agentName/events[{time,label}]/attempts}.
 *
 * <p>{@code events} reuses Task 10's {@link ShipmentEventDto} superset
 * shape as-is (it already carries {@code time}/{@code label}); the extra
 * fields on that DTO are harmless since the inspect view only reads those
 * two. {@code status} keeps the {@link ShipmentStatus} enum type so it
 * always serializes through {@link ShipmentStatus#getWireValue()}, never
 * {@code .name()}.
 */
public record RegisterInspectDto(
        String customerName,
        String address,
        ShipmentStatus status,
        String createdAt,
        String scheduledAt,
        String deliveredAt,
        String agentName,
        List<ShipmentEventDto> events,
        List<DeliveryAttemptDto> attempts
) {

    /**
     * Build from a shipment entity with eagerly loaded events/attempts/agent.
     */
    public static RegisterInspectDto from(Shipment s) {
        List<ShipmentEventDto> events = s.getEvents() != null
                ? s.getEvents().stream().map(ShipmentEventDto::from).toList()
                : List.of();
        List<DeliveryAttemptDto> attempts = s.getAttempts() != null
                ? s.getAttempts().stream().map(DeliveryAttemptDto::from).toList()
                : List.of();

        return new RegisterInspectDto(
                s.getCustomerName(),
                s.getDeliveryAddress(),
                s.getStatus(),
                TimeUtils.toIso(s.getCreatedAt()),
                TimeUtils.toIso(s.getScheduledDeliveryAt()),
                TimeUtils.toIso(s.getDeliveredAt()),
                s.getAssignedAgent() != null ? s.getAssignedAgent().getFullName() : null,
                events,
                attempts
        );
    }
}
