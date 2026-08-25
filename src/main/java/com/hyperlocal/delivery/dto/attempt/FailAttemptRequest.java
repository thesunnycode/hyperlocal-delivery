package com.hyperlocal.delivery.dto.attempt;

import com.hyperlocal.delivery.model.FailureReason;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/shipments/{id}/fail}.
 *
 * <p>Distinct from {@link CreateAttemptRequest} only in field naming:
 * the frontend's {@code shipmentsApi.js#logFailedAttempt} call sends
 * {@code {reason, notes}} rather than {@code {failureReason, notes}}.
 * {@code notes} already matches the backend field name exactly for this
 * one action, so (unlike other action DTOs in this codebase) no
 * {@code @JsonAlias} is needed here.
 */
public record FailAttemptRequest(
        @NotNull FailureReason reason,
        @Size(max = 1000) String notes
) {}
