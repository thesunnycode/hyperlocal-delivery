package com.hyperlocal.delivery.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hyperlocal.delivery.dto.analytics.AgentStats;
import com.hyperlocal.delivery.dto.analytics.DailyVolume;
import com.hyperlocal.delivery.dto.analytics.OverviewResponse;
import com.hyperlocal.delivery.dto.common.ApiSuccess;
import com.hyperlocal.delivery.security.CustomUserDetails;
import com.hyperlocal.delivery.service.AnalyticsService;
import com.hyperlocal.delivery.util.ResponseBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Analytics endpoints providing business-wide delivery metrics,
 * per-agent performance stats, and daily volume trends.
 * Restricted to {@code BUSINESS_OWNER} role.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('BUSINESS_OWNER')")
@Tag(name = "Analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    @Operation(summary = "Business-wide delivery metrics")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Overview metrics retrieved"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a business owner")
    })
    public ApiSuccess<OverviewResponse> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseBuilder.success(analyticsService.overview(user.getBusinessId(), from, to));
    }

    @GetMapping("/agents")
    @Operation(summary = "Per-agent performance metrics")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Agent stats retrieved"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a business owner")
    })
    public ApiSuccess<List<AgentStats>> agents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseBuilder.success(analyticsService.perAgent(user.getBusinessId(), from, to));
    }

    @GetMapping("/shipments/trend")
    @Operation(summary = "Daily shipment volume trend")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Trend data retrieved"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a business owner")
    })
    public ApiSuccess<List<DailyVolume>> trend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseBuilder.success(analyticsService.trend(user.getBusinessId(), from, to));
    }
}
