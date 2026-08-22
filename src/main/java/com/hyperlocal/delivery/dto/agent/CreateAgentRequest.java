package com.hyperlocal.delivery.dto.agent;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new delivery agent within the caller's business.
 *
 * <p>{@code password} is intentionally optional: the owner-facing "Add
 * agent" UI never collects one (agents are invited, not given a
 * owner-chosen password). When omitted or blank, {@link
 * com.hyperlocal.delivery.service.AgentService#create} generates a random
 * secure password server-side and hashes it the same way as every other
 * account; the agent is expected to reach a working login via the existing
 * forgot-password/reset-password flow, exactly like any other user.</p>
 *
 * <p>{@code fullName} accepts the frontend's {@code name} key via {@code
 * @JsonAlias} (the real "Add agent" form posts {@code {name, email, phone}},
 * never {@code fullName}), the same alias pattern already used for
 * {@code note}/{@code notes} elsewhere in this codebase.</p>
 */
public record CreateAgentRequest(
        @JsonAlias("name") @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotBlank @Email String email,
        // Optional: null, absent, or "" all mean "generate one server-side".
        // When a caller does supply a password it must still meet the same
        // minimum length as everywhere else in the app.
        @Pattern(regexp = "\\s*|.{8,}", message = "size must be between 8 and 2147483647")
        String password,
        @NotBlank @Pattern(regexp = "[+\\d\\s\\-]{7,20}") String phone
) {}
