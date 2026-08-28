package com.hyperlocal.delivery.dto.reports;

import java.util.List;

import com.hyperlocal.delivery.dto.analytics.DailyVolume;

/**
 * Daily shipment volume reshaped for {@code AdminTrendPage.jsx}. The page
 * reads {@code rows} for its table and {@code days} for its chart — both
 * carry the same per-day data, so both fields are populated from the same
 * source list.
 */
public record TrendReportDto(List<DayPointDto> rows, List<DayPointDto> days) {

    public static TrendReportDto from(List<DailyVolume> trend) {
        List<DayPointDto> points = trend.stream().map(DayPointDto::from).toList();
        return new TrendReportDto(points, points);
    }
}
