package com.hyperlocal.delivery.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hyperlocal.delivery.dto.attempt.FailAttemptRequest;
import com.hyperlocal.delivery.dto.common.ApiSuccess;
import com.hyperlocal.delivery.dto.common.ApiSuccessPage;
import com.hyperlocal.delivery.dto.shipment.AdvanceRequest;
import com.hyperlocal.delivery.dto.shipment.CreateShipmentRequest;
import com.hyperlocal.delivery.dto.shipment.ReassignRequest;
import com.hyperlocal.delivery.dto.shipment.ShipmentResponseDto;
import com.hyperlocal.delivery.dto.shipment.ShipmentSummaryDto;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.model.UserRole;
import com.hyperlocal.delivery.security.CustomUserDetails;
import com.hyperlocal.delivery.service.DeliveryAttemptService;
import com.hyperlocal.delivery.service.ShipmentService;
import com.hyperlocal.delivery.util.ResponseBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST endpoints for shipment lifecycle management.
 *
 * <p><strong>CRITICAL:</strong> {@code GET /api/shipments/mine} is
 * declared before {@code GET /api/shipments/{id}} to prevent Spring MVC from
 * treating "mine" as a path variable.
 */
@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
@Tag(name = "Shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final DeliveryAttemptService deliveryAttemptService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    @Operation(summary = "Create a new shipment with auto-assignment")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Shipment created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "404", description = "No available agents for auto-assignment")
    })
    public ApiSuccess<ShipmentResponseDto> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody CreateShipmentRequest request) {
        return ResponseBuilder.success(
                shipmentService.create(principal.getBusinessId(), principal.getUserId(), request));
    }

    @PostMapping("/{id}/pickup")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    @Operation(summary = "Agent action: confirm pickup (ASSIGNED → PICKED_UP)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shipment picked up"),
        @ApiResponse(responseCode = "404", description = "Shipment not found or not assigned to this agent"),
        @ApiResponse(responseCode = "422", description = "Shipment is not in ASSIGNED status")
    })
    public ApiSuccess<ShipmentResponseDto> pickup(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdvanceRequest request) {
        String notes = request != null ? request.notes() : null;
        return ResponseBuilder.success(shipmentService.pickup(principal, id, notes));
    }

    @PostMapping("/{id}/start-transit")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    @Operation(summary = "Agent action: start transit (PICKED_UP → IN_TRANSIT)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shipment in transit"),
        @ApiResponse(responseCode = "404", description = "Shipment not found or not assigned to this agent"),
        @ApiResponse(responseCode = "422", description = "Shipment is not in PICKED_UP status")
    })
    public ApiSuccess<ShipmentResponseDto> transit(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdvanceRequest request) {
        String notes = request != null ? request.notes() : null;
        return ResponseBuilder.success(shipmentService.transit(principal, id, notes));
    }

    @PostMapping("/{id}/out-for-delivery")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    @Operation(summary = "Agent action: out for delivery (IN_TRANSIT → OUT_FOR_DELIVERY)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shipment out for delivery"),
        @ApiResponse(responseCode = "404", description = "Shipment not found or not assigned to this agent"),
        @ApiResponse(responseCode = "422", description = "Shipment is not in IN_TRANSIT status")
    })
    public ApiSuccess<ShipmentResponseDto> outForDelivery(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdvanceRequest request) {
        String notes = request != null ? request.notes() : null;
        return ResponseBuilder.success(shipmentService.outForDelivery(principal, id, notes));
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    @Operation(summary = "Agent action: mark delivered (OUT_FOR_DELIVERY → DELIVERED, terminal)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shipment delivered"),
        @ApiResponse(responseCode = "404", description = "Shipment not found or not assigned to this agent"),
        @ApiResponse(responseCode = "422", description = "Shipment is not in OUT_FOR_DELIVERY status")
    })
    public ApiSuccess<ShipmentResponseDto> deliver(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdvanceRequest request) {
        String notes = request != null ? request.notes() : null;
        return ResponseBuilder.success(shipmentService.deliver(principal, id, notes));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    @Operation(summary = "Agent action: mark returned (OUT_FOR_DELIVERY → RETURNED, terminal)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shipment returned"),
        @ApiResponse(responseCode = "404", description = "Shipment not found or not assigned to this agent"),
        @ApiResponse(responseCode = "422", description = "Shipment is not in OUT_FOR_DELIVERY status")
    })
    public ApiSuccess<ShipmentResponseDto> returnShipment(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdvanceRequest request) {
        String notes = request != null ? request.notes() : null;
        return ResponseBuilder.success(shipmentService.returnShipment(principal, id, notes));
    }

    @PostMapping("/{id}/fail")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    @Operation(summary = "Agent action: log a failed delivery attempt "
            + "(OUT_FOR_DELIVERY → FAILED), appending one immutable attempt record")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Attempt recorded, shipment marked failed"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "404", description = "Shipment not found or not assigned to this agent"),
        @ApiResponse(responseCode = "422", description = "Shipment is not in OUT_FOR_DELIVERY status")
    })
    public ApiSuccess<ShipmentResponseDto> fail(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody FailAttemptRequest request) {
        return ResponseBuilder.success(
                deliveryAttemptService.recordFailure(principal, id, request.reason(), request.notes()));
    }

    @GetMapping
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    @Operation(summary = "List shipments with optional filters")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shipments listed successfully"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a business owner")
    })
    public ApiSuccessPage<ShipmentSummaryDto> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        Page<ShipmentSummaryDto> page = shipmentService.list(
                principal.getBusinessId(), status, agentId, from, to, pageable);
        return ResponseBuilder.page(page);
    }

    /**
     * MUST be declared before {@code /{id}} to avoid path variable collision.
     */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('DELIVERY_AGENT')")
    @Operation(summary = "List shipments assigned to the current agent")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Agent assignments listed"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a delivery agent")
    })
    public ApiSuccessPage<ShipmentSummaryDto> myAssignments(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) ShipmentStatus status,
            Pageable pageable) {
        Page<ShipmentSummaryDto> page = shipmentService.myAssignments(
                principal.getUserId(), status, pageable);
        return ResponseBuilder.page(page);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'DELIVERY_AGENT')")
    @Operation(summary = "Get full shipment detail",
            description = "Business owners get their business-scoped shipment; delivery "
                    + "agents get the shipment only if it is assigned to them (404, not "
                    + "403, otherwise).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shipment details retrieved"),
        @ApiResponse(responseCode = "404", description = "Shipment not found (or, for an "
                + "agent, not assigned to this agent)")
    })
    public ApiSuccess<ShipmentResponseDto> get(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id) {
        ShipmentResponseDto dto = principal.getRole() == UserRole.BUSINESS_OWNER
                ? shipmentService.get(principal.getBusinessId(), id)
                : shipmentService.getMyDetail(principal.getUserId(), id);
        return ResponseBuilder.success(dto);
    }

    @PostMapping("/{id}/reassign")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    @Operation(summary = "Owner's two mutations: reassign the agent on any non-terminal shipment, "
            + "and/or flip a FAILED shipment back to ASSIGNED")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shipment reassigned successfully"),
        @ApiResponse(responseCode = "404", description = "Shipment not found"),
        @ApiResponse(responseCode = "422",
                description = "Shipment is in a terminal status (DELIVERED or RETURNED), "
                        + "or the requested agent is invalid (not found, not a delivery agent, or inactive)")
    })
    public ApiSuccess<ShipmentResponseDto> reassign(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody ReassignRequest request) {
        return ResponseBuilder.success(
                shipmentService.reassign(principal.getBusinessId(), principal.getUserId(), id, request));
    }
}
