package com.hyperlocal.delivery.dto.reports;

import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.util.TimeUtils;

/**
 * One row of the admin register list, reshaped for {@code
 * AdminRegisterPage.jsx} (task 14 brief), which reads {@code
 * r.token/status/customerName/address/agentName/scheduledAt/deliveredAt}.
 *
 * <p>{@code status} keeps the {@link ShipmentStatus} enum type rather than
 * calling {@code .name()}, so it always serializes through the enum's
 * {@code @JsonValue}-annotated {@link ShipmentStatus#getWireValue()}.
 */
public record RegisterRowDto(
        String token,
        ShipmentStatus status,
        String customerName,
        String address,
        String agentName,
        String scheduledAt,
        String deliveredAt
) {

    /**
     * Build from a shipment entity.
     */
    public static RegisterRowDto from(Shipment s) {
        return new RegisterRowDto(
                s.getTrackingToken(),
                s.getStatus(),
                s.getCustomerName(),
                s.getDeliveryAddress(),
                s.getAssignedAgent() != null ? s.getAssignedAgent().getFullName() : null,
                TimeUtils.toIso(s.getScheduledDeliveryAt()),
                TimeUtils.toIso(s.getDeliveredAt())
        );
    }
}
