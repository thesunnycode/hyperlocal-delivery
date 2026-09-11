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
 * read by {@code AttemptLogList.jsx} (whose doc comment states the shape
 * as {@code {no, stamp, reason, note}} and whose render reads {@code
 * a.note} — singular — never {@code a.notes}).
 *
 * <p>{@code failureReason}/{@code reason} both go through {@link
 * com.hyperlocal.delivery.model.FailureReason#getLabel()} (Task 5's fix) —
 * never {@code .name()} — so Jackson's {@code @JsonValue} label mapping is
 * never bypassed.
 *
 * <p>Deliberately has no {@code notes} (plural) field — only the singular
 * {@code note} — since the frontend never reads {@code a.notes} and the
 * plural key must be verifiably absent from the wire shape (see Task 10's
 * {@code attemptDtoUsesSingularNoteFieldNotNotes} test).
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
