package com.hyperlocal.delivery.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hyperlocal.delivery.dto.agent.AgentResponse;
import com.hyperlocal.delivery.dto.agent.AgentSummaryDto;
import com.hyperlocal.delivery.dto.agent.CreateAgentRequest;
import com.hyperlocal.delivery.dto.agent.UpdateAgentRequest;
import com.hyperlocal.delivery.dto.common.ApiSuccess;
import com.hyperlocal.delivery.dto.common.ApiSuccessPage;
import com.hyperlocal.delivery.security.CustomUserDetails;
import com.hyperlocal.delivery.dto.invite.InviteResponse;
import com.hyperlocal.delivery.service.AgentInviteService;
import com.hyperlocal.delivery.service.AgentService;
import com.hyperlocal.delivery.util.ResponseBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST endpoints for delivery agent management. All endpoints require
 * the {@code BUSINESS_OWNER} role.
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('BUSINESS_OWNER')")
@Tag(name = "Agents")
public class AgentController {

    private final AgentService agentService;
    private final AgentInviteService agentInviteService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new delivery agent")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Agent created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    public ApiSuccess<AgentResponse> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody CreateAgentRequest request) {
        return ResponseBuilder.success(agentService.create(principal.getBusinessId(), request));
    }

    @GetMapping
    @Operation(summary = "List delivery agents with optional active filter")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Agents listed successfully"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a business owner")
    })
    public ApiSuccessPage<AgentSummaryDto> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) Boolean active,
            Pageable pageable) {
        Page<AgentSummaryDto> page = agentService.list(principal.getBusinessId(), active, pageable);
        return ResponseBuilder.page(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get agent details with delivery statistics")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Agent details retrieved"),
        @ApiResponse(responseCode = "404", description = "Agent not found")
    })
    public ApiSuccess<AgentResponse> get(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id) {
        return ResponseBuilder.success(agentService.get(principal.getBusinessId(), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update agent profile")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Agent updated successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "404", description = "Agent not found")
    })
    public ApiSuccess<AgentResponse> update(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateAgentRequest request) {
        return ResponseBuilder.success(agentService.update(principal.getBusinessId(), id, request));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete (deactivate) a delivery agent")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Agent deactivated successfully"),
        @ApiResponse(responseCode = "404", description = "Agent not found"),
        @ApiResponse(responseCode = "422", description = "Agent has active shipments")
    })
    public void deactivate(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id) {
        agentService.deactivate(principal.getBusinessId(), id);
    }

    @PostMapping("/{id}/reactivate")
    @Operation(summary = "Reactivate a previously deactivated delivery agent")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Agent reactivated successfully"),
        @ApiResponse(responseCode = "404", description = "Agent not found")
    })
    public ApiSuccess<AgentResponse> reactivate(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id) {
        return ResponseBuilder.success(agentService.reactivate(principal.getBusinessId(), id));
    }

    /**
     * Issue a fresh invite link so the agent can set their own password.
     *
     * <p>Re-issuable on purpose: creating the account is not the same event as
     * the agent reading the message, and an invite that can only be sent once
     * is an invite that gets lost. Each new link retires the previous one.
     */
    @PostMapping("/{id}/invite")
    @Operation(summary = "Issue an invite link that lets an agent set their password")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Invite issued"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a business owner"),
        @ApiResponse(responseCode = "404", description = "Agent not found in this business")
    })
    public ApiSuccess<InviteResponse> invite(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id) {
        return ResponseBuilder.success(agentInviteService.issue(principal.getBusinessId(), id));
    }
}
