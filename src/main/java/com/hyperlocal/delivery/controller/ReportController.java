package com.hyperlocal.delivery.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hyperlocal.delivery.dto.analytics.AgentStats;
import com.hyperlocal.delivery.dto.analytics.DailyVolume;
import com.hyperlocal.delivery.dto.analytics.FailureReasonStat;
import com.hyperlocal.delivery.dto.analytics.OverviewResponse;
import com.hyperlocal.delivery.dto.common.ApiSuccess;
import com.hyperlocal.delivery.dto.reports.AgentPerformanceRowDto;
import com.hyperlocal.delivery.dto.reports.OverviewReportDto;
import com.hyperlocal.delivery.dto.reports.RegisterInspectDto;
import com.hyperlocal.delivery.dto.reports.RegisterRowDto;
import com.hyperlocal.delivery.dto.reports.TrendReportDto;
import com.hyperlocal.delivery.exception.ValidationException;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.security.CustomUserDetails;
import com.hyperlocal.delivery.service.AnalyticsService;
import com.hyperlocal.delivery.service.ShipmentService;
import com.hyperlocal.delivery.util.CsvExportWriter;
import com.hyperlocal.delivery.util.ResponseBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Owner-facing reporting endpoints consumed directly by the admin frontend
 * ({@code AdminOverviewPage}, {@code AdminTrendPage},
 * {@code AdminAgentPerformancePage}).
 *
 * <p>This is a read-only reshaping layer over {@link AnalyticsService}: it
 * translates the frontend's {@code range}/{@code days} query params into
 * the {@code from}/{@code to} window the underlying service expects, then
 * maps the existing response types into DTOs matching the frontend's exact
 * field names. It does not duplicate any query logic and does not replace
 * {@link AnalyticsController}, which other consumers may still use.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('BUSINESS_OWNER')")
@Tag(name = "Reports")
public class ReportController {

    private final AnalyticsService analyticsService;
    private final ShipmentService shipmentService;

    @GetMapping("/overview")
    @Operation(summary = "Business-wide delivery overview, shaped for the admin overview page")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Overview report retrieved"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a business owner")
    })
    public ApiSuccess<OverviewReportDto> overview(
            @RequestParam(defaultValue = "7") int range,
            @AuthenticationPrincipal CustomUserDetails user) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(range);
        Long businessId = user.getBusinessId();

        OverviewResponse overview = analyticsService.overview(businessId, from, to);
        List<DailyVolume> days = analyticsService.trend(businessId, from, to);
        List<AgentStats> agents = analyticsService.perAgent(businessId, from, to);
        List<FailureReasonStat> reasons = analyticsService.failureReasonBreakdown(businessId, from, to);

        return ResponseBuilder.success(OverviewReportDto.from(overview, days, agents, reasons));
    }

    @GetMapping("/trend")
    @Operation(summary = "Daily shipment volume trend, shaped for the admin trend page")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Trend report retrieved"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a business owner")
    })
    public ApiSuccess<TrendReportDto> trend(
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal CustomUserDetails user) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days);

        List<DailyVolume> trend = analyticsService.trend(user.getBusinessId(), from, to);
        return ResponseBuilder.success(TrendReportDto.from(trend));
    }

    @GetMapping("/agent-performance")
    @Operation(summary = "Per-agent performance rows, shaped for the admin agent-performance page")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Agent performance report retrieved"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a business owner")
    })
    public ApiSuccess<List<AgentPerformanceRowDto>> agentPerformance(
            @RequestParam(defaultValue = "30") int range,
            @AuthenticationPrincipal CustomUserDetails user) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(range);

        List<AgentPerformanceRowDto> rows = analyticsService.perAgent(user.getBusinessId(), from, to)
                .stream().map(AgentPerformanceRowDto::from).toList();
        return ResponseBuilder.success(rows);
    }

    @GetMapping("/register")
    @Operation(summary = "Filterable shipment register list, shaped for the admin register page")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Register list retrieved"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a business owner")
    })
    public ApiSuccess<List<RegisterRowDto>> register(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal CustomUserDetails user) {
        List<ShipmentStatus> statuses = parseStatuses(status);

        List<RegisterRowDto> rows = shipmentService
                .searchForRegister(user.getBusinessId(), statuses, agentId, from, to, PageRequest.of(page, 20))
                .stream().map(RegisterRowDto::from).toList();
        return ResponseBuilder.success(rows);
    }

    /**
     * The {@code token} path variable is constrained to exclude the
     * literal {@code export} segment: without it, Spring MVC's path
     * matcher prefers this literal-prefixed pattern over
     * {@code /{kind}/export} below (literal segments outrank path
     * variables when comparing specificity), so {@code GET
     * /api/reports/register/export} would otherwise be swallowed here and
     * treated as a lookup for a tracking token literally named "export".
     */
    @GetMapping("/register/{token:(?!export$).*}")
    @Operation(summary = "Single-shipment inspect view by tracking token, for the admin register page")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Register inspect view retrieved"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a business owner"),
        @ApiResponse(responseCode = "404", description = "Shipment not found for this token (or token belongs to a different business)")
    })
    public ApiSuccess<RegisterInspectDto> registerInspect(
            @PathVariable String token,
            @AuthenticationPrincipal CustomUserDetails user) {
        RegisterInspectDto dto = RegisterInspectDto.from(
                shipmentService.getByTokenInBusiness(user.getBusinessId(), token));
        return ResponseBuilder.success(dto);
    }

    /**
     * Raw CSV download for {@code agent-performance} or {@code register},
     * reusing the exact same underlying queries and row DTOs as the
     * corresponding JSON list endpoints above (same query params, same
     * shaping) so the exported columns never drift from what the frontend
     * table renders.
     *
     * <p>Deliberately bypasses the {@link ApiSuccess} envelope: this is a
     * binary/text file download, not a JSON API response, so the body is
     * the raw CSV text and the content type is {@code text/csv}.
     */
    @GetMapping("/{kind}/export")
    @Operation(summary = "CSV export of the agent-performance or register report")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "CSV export generated", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "text/csv")),
        @ApiResponse(responseCode = "400", description = "Unknown export kind"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a business owner")
    })
    public ResponseEntity<byte[]> export(
            @PathVariable String kind,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "30") int range,
            @AuthenticationPrincipal CustomUserDetails user) {
        String csv = switch (kind) {
            case "agent-performance" -> {
                LocalDate toDate = LocalDate.now();
                LocalDate fromDate = toDate.minusDays(range);
                List<AgentPerformanceRowDto> rows = analyticsService
                        .perAgent(user.getBusinessId(), fromDate, toDate)
                        .stream().map(AgentPerformanceRowDto::from).toList();
                yield CsvExportWriter.agentPerformance(rows);
            }
            case "register" -> {
                List<ShipmentStatus> statuses = parseStatuses(status);
                List<RegisterRowDto> rows = shipmentService
                        .searchForRegister(user.getBusinessId(), statuses, agentId, from, to, Pageable.unpaged())
                        .stream().map(RegisterRowDto::from).toList();
                yield CsvExportWriter.register(rows);
            }
            default -> throw new ValidationException("Unknown export kind: " + kind);
        };
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + kind + ".csv\"")
                .body(bytes);
    }

    /**
     * Parses a comma-separated {@code status} query param into
     * {@link ShipmentStatus} values, surfacing an unrecognized value as a
     * {@link ValidationException} (400) rather than letting
     * {@link ShipmentStatus#fromWireValue}'s {@link IllegalArgumentException}
     * fall through to the generic 500 handler.
     *
     * <p>Unlike {@code /api/shipments}, this endpoint accepts a
     * comma-separated list rather than a single value, so it can't rely on
     * the registered {@code ShipmentStatusConverter} and must parse (and
     * validate) it manually here.
     */
    private static List<ShipmentStatus> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return List.of();
        }
        return Arrays.stream(status.split(","))
                .map(String::trim)
                .map(value -> {
                    try {
                        return ShipmentStatus.fromWireValue(value);
                    } catch (IllegalArgumentException ex) {
                        throw new ValidationException("Unknown shipment status: " + value);
                    }
                })
                .toList();
    }
}
