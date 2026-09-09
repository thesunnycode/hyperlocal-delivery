package com.hyperlocal.delivery.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hyperlocal.delivery.dto.analytics.DailyVolume;
import com.hyperlocal.delivery.dto.analytics.FailureReasonStat;
import com.hyperlocal.delivery.dto.analytics.OverviewResponse;
import com.hyperlocal.delivery.exception.ValidationException;
import com.hyperlocal.delivery.model.FailureReason;
import com.hyperlocal.delivery.repository.ShipmentRepository;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(shipmentRepository);
    }

    // ─── overview tests ──────────────────────────────────────────────

    @Test
    void overview_returnsCorrectMetrics_whenDataExists() {
        Object[] overview = new Object[]{100L, 80L, 10L, 5L, 5L, 2.5};
        Object[] onTime = new Object[]{60L, 80L};
        Object[] firstAttempt = new Object[]{80L, 70L};

        when(shipmentRepository.overviewRaw(eq(1L), any(), any()))
                .thenReturn(listOf(overview));
        when(shipmentRepository.onTimeRateRaw(eq(1L), any(), any()))
                .thenReturn(listOf(onTime));
        when(shipmentRepository.firstAttemptSuccessRaw(eq(1L), any(), any()))
                .thenReturn(listOf(firstAttempt));

        OverviewResponse result = analyticsService.overview(1L, LocalDate.now().minusDays(7), LocalDate.now());

        assertEquals(100L, result.totalShipments());
        assertEquals(80L, result.delivered());
        assertEquals(10L, result.failed());
        assertEquals(5L, result.returned());
        assertEquals(5L, result.inProgress());
        assertEquals(2.5, result.avgDeliveryTimeHours());
        assertEquals(0.75, result.onTimeRate(), 0.001);         // 60/80
        assertEquals(0.875, result.firstAttemptSuccessRate(), 0.001); // 70/80
    }

    @Test
    void overview_handlesNullResults_gracefully() {
        when(shipmentRepository.overviewRaw(eq(1L), any(), any()))
                .thenReturn(listOf(new Object[]{null, null, null, null, null, null}));
        when(shipmentRepository.onTimeRateRaw(eq(1L), any(), any()))
                .thenReturn(listOf(new Object[]{null, null}));
        when(shipmentRepository.firstAttemptSuccessRaw(eq(1L), any(), any()))
                .thenReturn(listOf(new Object[]{null, null}));

        OverviewResponse result = analyticsService.overview(1L, LocalDate.now().minusDays(7), LocalDate.now());

        assertEquals(0L, result.totalShipments());
        assertEquals(0L, result.delivered());
        assertEquals(0.0, result.onTimeRate());
        assertEquals(0.0, result.firstAttemptSuccessRate());
    }

    @Test
    void overview_handlesEmptyResults() {
        when(shipmentRepository.overviewRaw(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());
        when(shipmentRepository.onTimeRateRaw(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());
        when(shipmentRepository.firstAttemptSuccessRaw(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        OverviewResponse result = analyticsService.overview(1L, LocalDate.now().minusDays(7), LocalDate.now());

        assertEquals(0L, result.totalShipments());
        assertEquals(0.0, result.avgDeliveryTimeHours());
    }

    @Test
    void overview_defaultsToLast30Days_whenNoDatesProvided() {
        when(shipmentRepository.overviewRaw(eq(1L), any(), any()))
                .thenReturn(listOf(new Object[]{0L, 0L, 0L, 0L, 0L, 0.0}));
        when(shipmentRepository.onTimeRateRaw(eq(1L), any(), any()))
                .thenReturn(listOf(new Object[]{0L, 0L}));
        when(shipmentRepository.firstAttemptSuccessRaw(eq(1L), any(), any()))
                .thenReturn(listOf(new Object[]{0L, 0L}));

        OverviewResponse result = analyticsService.overview(1L, null, null);

        assertNotNull(result);
        assertEquals(LocalDate.now().minusDays(30).toString(), result.dateFrom());
        assertEquals(LocalDate.now().toString(), result.dateTo());
    }

    @Test
    void overview_invalidRange_throws() {
        assertThrows(ValidationException.class,
                () -> analyticsService.overview(1L, LocalDate.now(), LocalDate.now().minusDays(7)));
    }

    // ─── trend tests ─────────────────────────────────────────────────

    @Test
    void trend_returnsCorrectDailyVolumes() {
        Object[] row1 = new Object[]{"2025-01-01", 10L, 8L, 1L, 1L};
        Object[] row2 = new Object[]{"2025-01-02", 12L, 10L, 0L, 2L};

        when(shipmentRepository.dailyVolumeRaw(eq(1L), any(), any()))
                .thenReturn(listOf(row1, row2));

        List<DailyVolume> result = analyticsService.trend(1L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2));

        assertEquals(2, result.size());
        assertEquals("2025-01-01", result.get(0).getDate());
        assertEquals(10L, result.get(0).getTotal());
        assertEquals(8L, result.get(0).getDelivered());
    }

    @Test
    void trend_rejectsRangeExceeding365Days() {
        assertThrows(ValidationException.class,
                () -> analyticsService.trend(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 2, 1)));
    }

    // ─── failureReasonBreakdown tests ────────────────────────────────

    @Test
    void failureReasonBreakdown_zeroFillsMissingReasons() {
        // Only one reason returned from DB
        Object[] row = new Object[]{"CUSTOMER_ABSENT", 5L};
        when(shipmentRepository.failureReasonBreakdownRaw(eq(1L), any(), any()))
                .thenReturn(listOf(row));

        List<FailureReasonStat> result = analyticsService.failureReasonBreakdown(
                1L, LocalDate.now().minusDays(7), LocalDate.now());

        // All enum values should be present
        assertEquals(FailureReason.values().length, result.size());

        // CUSTOMER_ABSENT should have count 5
        FailureReasonStat absent = result.stream()
                .filter(s -> s.reason() == FailureReason.CUSTOMER_ABSENT)
                .findFirst().orElseThrow();
        assertEquals(5L, absent.count());

        // Others should be zero-filled
        FailureReasonStat other = result.stream()
                .filter(s -> s.reason() == FailureReason.OTHER)
                .findFirst().orElseThrow();
        assertEquals(0L, other.count());
    }

    @Test
    void failureReasonBreakdown_sortedDescendingByCount() {
        Object[] row1 = new Object[]{"CUSTOMER_ABSENT", 2L};
        Object[] row2 = new Object[]{"REFUSED", 10L};
        when(shipmentRepository.failureReasonBreakdownRaw(eq(1L), any(), any()))
                .thenReturn(listOf(row1, row2));

        List<FailureReasonStat> result = analyticsService.failureReasonBreakdown(
                1L, LocalDate.now().minusDays(7), LocalDate.now());

        // First element should be the highest count
        assertEquals(FailureReason.REFUSED, result.get(0).reason());
        assertEquals(10L, result.get(0).count());
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    /**
     * Create a properly typed {@code List<Object[]>} that doesn't get
     * flattened by Java's varargs inference. {@code List.of(new Object[]{...})}
     * triggers varargs expansion and produces {@code List<Object>} instead.
     */
    @SafeVarargs
    private static List<Object[]> listOf(Object[]... rows) {
        List<Object[]> list = new ArrayList<>(rows.length);
        Collections.addAll(list, rows);
        return list;
    }
}
