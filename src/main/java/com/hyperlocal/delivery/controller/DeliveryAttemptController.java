package com.hyperlocal.delivery.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hyperlocal.delivery.dto.attempt.CreateAttemptRequest;
import com.hyperlocal.delivery.dto.common.ApiSuccess;
import com.hyperlocal.delivery.dto.shipment.DeliveryAttemptDto;
import com.hyperlocal.delivery.security.CustomUserDetails;
import com.hyperlocal.delivery.service.DeliveryAttemptService;
import com.hyperlocal.delivery.util.ResponseBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST endpoints for delivery attempt management.
 */
@RestController
@RequestMapping("/api/shipments/{shipmentId}")
@RequiredArgsConstructor
@Tag(name = "Delivery Attempts")
public class DeliveryAttemptController {

    private final DeliveryAttemptService deliveryAttemptService;

    @PostMapping("/attempt")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    @Operation(summary = "Record a failed delivery attempt")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Attempt recorded successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "404", description = "Shipment not found or not assigned to this agent")
    })
    public ApiSuccess<DeliveryAttemptDto> record(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long shipmentId,
            @Valid @RequestBody CreateAttemptRequest request) {
        return ResponseBuilder.success(
                deliveryAttemptService.record(principal, shipmentId, request));
    }

    @GetMapping("/attempts")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'DELIVERY_AGENT')")
    @Operation(summary = "List delivery attempts for a shipment (owner: any in their business; agent: their own shipment only)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Attempts listed successfully"),
        @ApiResponse(responseCode = "404", description = "Shipment not found or not visible to this caller")
    })
    public ApiSuccess<List<DeliveryAttemptDto>> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long shipmentId) {
        return ResponseBuilder.success(
                deliveryAttemptService.listForShipment(principal, shipmentId));
    }
}
