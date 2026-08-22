package com.hyperlocal.delivery.dto.invite;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Spending an invite: the token from the link, plus the password the agent
 * chooses. The 8-character floor matches the reset flow, so the two ways of
 * setting a password cannot disagree.
 */
public record AcceptInviteRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 100) String password) {
}
