package com.hyperlocal.delivery.dto.reports;

/**
 * One failure-reason breakdown row for the overview's "Why deliveries
 * fail" panel. {@code label} is the {@link com.hyperlocal.delivery.model.FailureReason}'s
 * human-readable label (its {@code @JsonValue} getter), never the raw enum
 * constant name.
 */
public record ReasonCountDto(String label, long count, double pct) {
}
