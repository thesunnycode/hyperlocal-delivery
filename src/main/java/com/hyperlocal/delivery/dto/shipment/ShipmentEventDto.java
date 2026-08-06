package com.hyperlocal.delivery.dto.shipment;

import java.util.EnumMap;
import java.util.Map;

import com.hyperlocal.delivery.model.ShipmentEvent;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.util.TimeUtils;

/**
 * DTO for a single shipment status-transition event.
 *
 * <p>Superset shape covering all three frontend timeline readers:
 * owner/agent pages read {@code toStatus || status} plus {@code label} and
 * {@code meta}; the customer tracking page reads {@code status}, {@code
 * label}, and {@code stamp}; the admin register inspect view reads {@code
 * time} and {@code label}. {@code status} and {@code toStatus} are aliases
 * of the same value, as are {@code stamp} and {@code time} (both ISO-8601,
 * same instant), and {@code meta} aliases {@code notes}.
 */
public record ShipmentEventDto(
        ShipmentStatus fromStatus,
        ShipmentStatus toStatus,
        ShipmentStatus status,
        String changedBy,
        String notes,
        String meta,
        String label,
        String createdAt,
        String stamp,
        String time
) {

    private static final Map<ShipmentStatus, String> STATUS_LABELS = new EnumMap<>(ShipmentStatus.class);

    static {
        STATUS_LABELS.put(ShipmentStatus.ASSIGNED, "Assigned");
        STATUS_LABELS.put(ShipmentStatus.PICKED_UP, "Picked up");
        STATUS_LABELS.put(ShipmentStatus.IN_TRANSIT, "In transit");
        STATUS_LABELS.put(ShipmentStatus.OUT_FOR_DELIVERY, "Out for delivery");
        STATUS_LABELS.put(ShipmentStatus.DELIVERED, "Delivered");
        STATUS_LABELS.put(ShipmentStatus.FAILED, "Failed");
        STATUS_LABELS.put(ShipmentStatus.RETURNED, "Returned");
        STATUS_LABELS.put(ShipmentStatus.CANCELLED, "Cancelled");
    }

    /**
     * Build from a shipment event entity.
     */
    public static ShipmentEventDto from(ShipmentEvent e) {
        String iso = TimeUtils.toIso(e.getCreatedAt());
        return new ShipmentEventDto(
                e.getFromStatus(),
                e.getToStatus(),
                e.getToStatus(),
                e.getChangedBy() != null ? e.getChangedBy().getFullName() : null,
                e.getNotes(),
                e.getNotes(),
                labelFor(e.getToStatus()),
                iso,
                iso,
                iso
        );
    }

    /**
     * The human-readable label for a status, shared with other
     * timeline-shaped DTOs (e.g. {@link com.hyperlocal.delivery.dto.tracking.PublicTrackingResponse})
     * so every frontend timeline reader sees the same wording.
     */
    public static String labelFor(ShipmentStatus status) {
        return status != null ? STATUS_LABELS.get(status) : null;
    }
}
