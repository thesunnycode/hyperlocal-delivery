package com.hyperlocal.delivery.dto.agent;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating an existing delivery agent's profile.
 *
 * <p>{@code fullName} accepts the frontend's {@code name} key via {@code
 * @JsonAlias} (the real "Edit agent" form posts {@code {name, phone}},
 * never {@code fullName}), the same alias pattern already used for
 * {@code note}/{@code notes} elsewhere in this codebase.</p>
 */
public record UpdateAgentRequest(
        @JsonAlias("name") @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotBlank @Pattern(regexp = "[+\\d\\s\\-]{7,20}") String phone
) {}
