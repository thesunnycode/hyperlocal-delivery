package com.hyperlocal.delivery.dto.analytics;

/**
 * Business-wide delivery metrics for a given date range.
 */
public record OverviewResponse(
        String dateFrom,
        String dateTo,
        long totalShipments,
        long delivered,
        long failed,
        long returned,
        long inProgress,
        double onTimeRate,
        double avgDeliveryTimeHours,
        double firstAttemptSuccessRate
) {}
