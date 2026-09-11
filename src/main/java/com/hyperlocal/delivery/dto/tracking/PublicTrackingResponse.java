package com.hyperlocal.delivery.dto.tracking;

import java.util.List;

import com.hyperlocal.delivery.dto.shipment.ShipmentEventDto;
import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.util.TimeUtils;

/**
 * Public tracking response that deliberately excludes sensitive information:
 * customerPhone, agent identity, internal IDs, and delivery attempts.
 *
 * <p>Field names match the real frontend contract directly (renamed, not
 * aliased, per the same response-side precedent used for {@code
 * ShipmentResponseDto}/{@code ShipmentSummaryDto} in this fix):
 * CustomerTrackingPage.jsx reads {@code s.address}, {@code s.scheduledAt},
 * and {@code s.events} (each item read as {@code {status, label, stamp}}) —
 * not {@code deliveryAddress}, {@code scheduledDeliveryAt}, or a {@code
 * timeline} array of {@code {status, at}}. This is a rename-only fix: no
 * field beyond this existing set (trackingToken, status, customerName,
 * address, scheduledAt, deliveredAt, businessName, businessPhone, events) is
 * added — agent identity, agent phone, internal ids, and attempt data
 * remain excluded.
 */
public record PublicTrackingResponse(
        String trackingToken,
        ShipmentStatus status,
        String customerName,
        String address,
        String scheduledAt,
        String deliveredAt,
        String businessName,
        String businessPhone,
        List<TimelineItem> events
) {

    /**
     * A single timeline entry: the status reached, its human-readable label
     * (shared with {@link ShipmentEventDto#labelFor}, the same status-label
     * map used by the owner/agent/admin timeline views), and when it
     * happened.
     */
    public record TimelineItem(ShipmentStatus status, String label, String stamp) {}

    /**
     * Build from a shipment entity. Excludes customerPhone, agent identity,
     * internal numeric IDs, and delivery attempt details.
     */
    public static PublicTrackingResponse from(Shipment s) {
        List<TimelineItem> events = List.of();
        if (s.getEvents() != null) {
            events = s.getEvents().stream()
                    .map(e -> new TimelineItem(
                            e.getToStatus(),
                            ShipmentEventDto.labelFor(e.getToStatus()),
                            TimeUtils.toIso(e.getCreatedAt())))
                    .toList();
        }

        return new PublicTrackingResponse(
                s.getTrackingToken(),
                s.getStatus(),
                s.getCustomerName(),
                s.getDeliveryAddress(),
                TimeUtils.toIso(s.getScheduledDeliveryAt()),
                TimeUtils.toIso(s.getDeliveredAt()),
                s.getBusiness() != null ? s.getBusiness().getName() : null,
                s.getBusiness() != null ? s.getBusiness().getPhone() : null,
                events
        );
    }
}
