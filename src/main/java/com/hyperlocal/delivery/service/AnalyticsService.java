package com.hyperlocal.delivery.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hyperlocal.delivery.dto.analytics.AgentStats;
import com.hyperlocal.delivery.dto.analytics.DailyVolume;
import com.hyperlocal.delivery.dto.analytics.FailureReasonStat;
import com.hyperlocal.delivery.dto.analytics.OverviewResponse;
import com.hyperlocal.delivery.exception.ValidationException;
import com.hyperlocal.delivery.model.FailureReason;
import com.hyperlocal.delivery.repository.ShipmentRepository;
import com.hyperlocal.delivery.util.TimeUtils;

/**
 * Computes analytics metrics for a business tenant. Delegates heavy
 * aggregation to the database via native and JPQL queries, then
 * performs lightweight post-processing (rate calculations, date
 * formatting) in Java.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final ShipmentRepository shipmentRepository;

    /**
     * Business-wide delivery overview for the given date range.
     * Defaults to the last 30 days if no range is specified.
     */
    public OverviewResponse overview(Long businessId, LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        validateRange(effectiveFrom, effectiveTo);

        LocalDateTime start = TimeUtils.startOfDay(effectiveFrom);
        LocalDateTime end = TimeUtils.endOfDay(effectiveTo);

        Object[] raw = shipmentRepository.overviewRaw(businessId, start, end)
                .stream().findFirst().orElse(new Object[]{0L, 0L, 0L, 0L, 0L, 0.0});
        Object[] onTimeRaw = shipmentRepository.onTimeRateRaw(businessId, start, end)
                .stream().findFirst().orElse(new Object[]{0L, 0L});
        Object[] firstAttemptRaw = shipmentRepository.firstAttemptSuccessRaw(businessId, start, end)
                .stream().findFirst().orElse(new Object[]{0L, 0L});

        long total = toLong(raw[0]);
        long delivered = toLong(raw[1]);
        long failed = toLong(raw[2]);
        long returned = toLong(raw[3]);
        long inProgress = toLong(raw[4]);
        double avgHours = toDouble(raw[5]);

        double onTimeRate = 0.0;
        if (onTimeRaw.length >= 2) {
            long onTime = toLong(onTimeRaw[0]);
            long totalDelivered = toLong(onTimeRaw[1]);
            onTimeRate = totalDelivered > 0 ? (double) onTime / totalDelivered : 0.0;
        }

        double firstAttemptSuccessRate = 0.0;
        if (firstAttemptRaw.length >= 2) {
            long totalDelivered = toLong(firstAttemptRaw[0]);
            long firstSuccess = toLong(firstAttemptRaw[1]);
            firstAttemptSuccessRate = totalDelivered > 0 ? (double) firstSuccess / totalDelivered : 0.0;
        }

        return new OverviewResponse(
                effectiveFrom.toString(),
                effectiveTo.toString(),
                total,
                delivered,
                failed,
                returned,
                inProgress,
                onTimeRate,
                avgHours,
                firstAttemptSuccessRate
        );
    }

    /**
     * Per-agent performance metrics for the given date range.
     */
    public List<AgentStats> perAgent(Long businessId, LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        validateRange(effectiveFrom, effectiveTo);

        LocalDateTime start = TimeUtils.startOfDay(effectiveFrom);
        LocalDateTime end = TimeUtils.endOfDay(effectiveTo);

        long daysBetween = ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) + 1;

        List<AgentStats> stats = shipmentRepository.agentStats(businessId, start, end);
        for (AgentStats s : stats) {
            double avg = daysBetween > 0 ? (double) s.getTotalAssigned() / daysBetween : 0.0;
            s.withAvgShipmentsPerDay(avg);
        }
        return stats;
    }

    /**
     * Daily shipment volume trend for the given date range.
     * Maximum range: 365 days.
     */
    public List<DailyVolume> trend(Long businessId, LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        validateRange(effectiveFrom, effectiveTo);

        long daysBetween = ChronoUnit.DAYS.between(effectiveFrom, effectiveTo);
        if (daysBetween > 365) {
            throw new ValidationException("Date range must not exceed 365 days");
        }

        LocalDateTime start = TimeUtils.startOfDay(effectiveFrom);
        LocalDateTime end = TimeUtils.endOfDay(effectiveTo);

        List<Object[]> rawRows = shipmentRepository.dailyVolumeRaw(businessId, start, end);
        List<DailyVolume> result = new ArrayList<>(rawRows.size());
        for (Object[] row : rawRows) {
            String date = row[0] != null ? row[0].toString() : null;
            long total = toLong(row[1]);
            long delivered = toLong(row[2]);
            long failed = toLong(row[3]);
            long inProgress = toLong(row[4]);
            result.add(new DailyVolume(date, total, delivered, failed, inProgress));
        }
        return result;
    }

    /**
     * Failed-delivery-attempt breakdown by {@link FailureReason} for the
     * given date range. Every enum constant is represented (zero-filled for
     * reasons with no occurrences) and the list is sorted descending by
     * count, matching how the frontend renders the "why deliveries fail"
     * panel (highest-count reason first).
     */
    public List<FailureReasonStat> failureReasonBreakdown(Long businessId, LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        validateRange(effectiveFrom, effectiveTo);

        LocalDateTime start = TimeUtils.startOfDay(effectiveFrom);
        LocalDateTime end = TimeUtils.endOfDay(effectiveTo);

        Map<FailureReason, Long> counts = new EnumMap<>(FailureReason.class);
        for (FailureReason reason : FailureReason.values()) {
            counts.put(reason, 0L);
        }
        for (Object[] row : shipmentRepository.failureReasonBreakdownRaw(businessId, start, end)) {
            FailureReason reason = FailureReason.valueOf(row[0].toString());
            counts.put(reason, toLong(row[1]));
        }

        List<FailureReasonStat> result = new ArrayList<>(counts.size());
        for (Map.Entry<FailureReason, Long> entry : counts.entrySet()) {
            result.add(new FailureReasonStat(entry.getKey(), entry.getValue()));
        }
        result.sort(Comparator.comparingLong(FailureReasonStat::count).reversed());
        return result;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ValidationException("'from' date must not be after 'to' date");
        }
    }

    private static long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }

    private static double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof BigDecimal bd) return bd.doubleValue();
        return Double.parseDouble(value.toString());
    }
}
