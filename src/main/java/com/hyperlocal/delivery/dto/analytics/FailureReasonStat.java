package com.hyperlocal.delivery.dto.analytics;

import com.hyperlocal.delivery.model.FailureReason;

/**
 * Count of failed delivery attempts for a single {@link FailureReason}
 * within a date range. Every enum value is represented (zero-filled),
 * sorted descending by count, so report consumers always see the full
 * breakdown rather than only the reasons that happened to occur.
 */
public record FailureReasonStat(FailureReason reason, long count) {
}
