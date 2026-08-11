package com.hyperlocal.delivery.dto.agent;

import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.util.TimeUtils;

/**
 * Lightweight agent summary for paginated list endpoints.
 */
public record AgentSummaryDto(
        Long id,
        String name,
        String email,
        String phone,
        Boolean active,
        Long openCount,
        String createdAt,
        /** True once this agent has spent an invite (or reset link) and set
         *  their own password — see {@link User#getPasswordChangedAt()}. An
         *  agent created but never activated still has the random,
         *  nobody-knows-it password {@code AgentService} generated, so
         *  reissuing them an invite link is the normal case, not a mistake to
         *  guard against; re-inviting an already-activated agent is the one
         *  the owner console asks to confirm. */
        Boolean activated
) {

    /**
     * Build a summary from a user entity and active shipment count.
     */
    public static AgentSummaryDto from(User user, long openCount) {
        return new AgentSummaryDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getIsActive(),
                openCount,
                TimeUtils.toIso(user.getCreatedAt()),
                user.getPasswordChangedAt() != null
        );
    }
}
