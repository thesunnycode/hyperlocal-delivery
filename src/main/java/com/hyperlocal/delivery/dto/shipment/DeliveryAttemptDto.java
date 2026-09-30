package com.hyperlocal.delivery.dto.shipment;

import com.hyperlocal.delivery.model.DeliveryAttempt;
import com.hyperlocal.delivery.util.TimeUtils;

/**
 * DTO for a delivery attempt record.
 *
 * <p>Superset shape: keeps the original field names ({@code attemptNumber},
 * {@code failureReason}, {@code attemptedAt}) used by the standalone
 * attempt endpoints ({@code POST /attempt}, {@code GET /attempts}), and
 * adds the aliases {@code no}, {@code reason}, {@code note}, {@code stamp}
 * read by the delivery-attempt lists in {@code OwnerLive.tsx} and
 * {@code RiderLive.tsx}, which render {@code a.note} — singular — never
 * {@code a.notes}.
 *
 * <p>{@code failureReason}/{@code reason} both go through {@link
 * com.hyperlocal.delivery.model.FailureReason#getLabel()} —
 * never {@code .name()} — so Jackson's {@code @JsonValue} label mapping is
 * never bypassed.
 *
 * <p>Deliberately has no {@code notes} (plural) field — only the singular
 * {@code note} — since the frontend never reads {@code a.notes}, the
 * plural key is kept out of the wire shape entirely (the
 * {@code attemptDtoUsesSingularNoteFieldNotNotes} test checks this).
 */
public record DeliveryAttemptDto(
        Long id,
        Long shipmentId,
        Long agentId,
        String agentName,
        Integer attemptNumber,
        Integer no,
        String failureReason,
        String reason,
        String note,
        String attemptedAt,
        String stamp
) {

    /**
     * Build from a delivery attempt entity.
     */
    public static DeliveryAttemptDto from(DeliveryAttempt a) {
        String reasonLabel = a.getFailureReason() != null ? a.getFailureReason().getLabel() : null;
        String stamp = TimeUtils.toIso(a.getAttemptedAt());
        return new DeliveryAttemptDto(
                a.getId(),
                a.getShipment() != null ? a.getShipment().getId() : null,
                a.getAgent() != null ? a.getAgent().getId() : null,
                a.getAgent() != null ? a.getAgent().getFullName() : null,
                a.getAttemptNumber(),
                a.getAttemptNumber(),
                reasonLabel,
                reasonLabel,
                a.getNotes(),
                stamp,
                stamp
        );
    }
}
