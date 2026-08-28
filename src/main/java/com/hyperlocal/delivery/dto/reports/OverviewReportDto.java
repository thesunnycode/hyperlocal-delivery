package com.hyperlocal.delivery.dto.reports;

import java.util.Comparator;
import java.util.List;

import com.hyperlocal.delivery.dto.analytics.AgentStats;
import com.hyperlocal.delivery.dto.analytics.DailyVolume;
import com.hyperlocal.delivery.dto.analytics.FailureReasonStat;
import com.hyperlocal.delivery.dto.analytics.OverviewResponse;

/**
 * Business-wide overview reshaped into the exact field names read by
 * {@code AdminOverviewPage.jsx} (see task 13 brief). Rates coming out of
 * {@link com.hyperlocal.delivery.service.AnalyticsService} are fractions
 * (0.0-1.0); this DTO expresses them as 0-100 percentages, matching the
 * frontend's {@code `${data.onTimeRate}%`} rendering.
 */
public record OverviewReportDto(
        long total,
        long delivered,
        double deliveredPct,
        long failed,
        double failedPct,
        long returned,
        double returnedPct,
        long inProgress,
        double inProgressPct,
        double onTimeRate,
        double avgDeliveryHours,
        double firstAttemptRate,
        List<DayPointDto> days,
        List<AgentBarDto> agentBars,
        List<ReasonCountDto> reasons
) {

    private static final int TOP_AGENT_BARS = 5;

    public static OverviewReportDto from(OverviewResponse overview,
                                          List<DailyVolume> trendDays,
                                          List<AgentStats> agents,
                                          List<FailureReasonStat> reasonBreakdown) {
        long total = overview.totalShipments();

        List<DayPointDto> days = trendDays.stream().map(DayPointDto::from).toList();

        List<AgentStats> topAgents = agents.stream()
                .sorted(Comparator.comparingLong(AgentStats::getTotalDelivered).reversed())
                .limit(TOP_AGENT_BARS)
                .toList();
        long maxDelivered = topAgents.stream().mapToLong(AgentStats::getTotalDelivered).max().orElse(0L);
        List<AgentBarDto> agentBars = topAgents.stream()
                .map(a -> new AgentBarDto(a.getAgentName(), a.getTotalDelivered(), pct(a.getTotalDelivered(), maxDelivered)))
                .toList();

        long totalFailedAttempts = reasonBreakdown.stream().mapToLong(FailureReasonStat::count).sum();
        List<ReasonCountDto> reasons = reasonBreakdown.stream()
                .map(r -> new ReasonCountDto(r.reason().getLabel(), r.count(), pct(r.count(), totalFailedAttempts)))
                .toList();

        return new OverviewReportDto(
                total,
                overview.delivered(),
                pct(overview.delivered(), total),
                overview.failed(),
                pct(overview.failed(), total),
                overview.returned(),
                pct(overview.returned(), total),
                overview.inProgress(),
                pct(overview.inProgress(), total),
                round1(overview.onTimeRate() * 100),
                round1(overview.avgDeliveryTimeHours()),
                round1(overview.firstAttemptSuccessRate() * 100),
                days,
                agentBars,
                reasons
        );
    }

    private static double pct(long part, long total) {
        if (total <= 0) return 0.0;
        return round1(part * 100.0 / total);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
