package com.hyperlocal.delivery.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hyperlocal.delivery.dto.common.ApiSuccess;
import com.hyperlocal.delivery.dto.tracking.PublicTrackingResponse;
import com.hyperlocal.delivery.service.ShipmentService;
import com.hyperlocal.delivery.util.ResponseBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Public (unauthenticated) endpoint for tracking a shipment by its opaque
 * tracking token. No {@code @PreAuthorize} — accessible without JWT.
 */
@RestController
@RequestMapping("/api/track")
@RequiredArgsConstructor
@Tag(name = "Public Tracking")
public class PublicTrackingController {

    private final ShipmentService shipmentService;

    @GetMapping("/{token}")
    @Operation(summary = "Track shipment by public token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tracking info retrieved"),
        @ApiResponse(responseCode = "404", description = "Invalid tracking token")
    })
    public ApiSuccess<PublicTrackingResponse> track(@PathVariable String token) {
        return ResponseBuilder.success(shipmentService.trackPublic(token));
    }
}
