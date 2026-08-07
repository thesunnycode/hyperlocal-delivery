package com.hyperlocal.delivery.dto.attempt;

import com.hyperlocal.delivery.model.FailureReason;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for recording a failed delivery attempt.
 */
public record CreateAttemptRequest(
        @NotNull FailureReason failureReason,
        @Size(max = 1000) String notes
) {}
