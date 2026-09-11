package com.hyperlocal.delivery.dto.agent;

import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.util.TimeUtils;

/**
 * Full agent response DTO with optional delivery statistics.
 */
public record AgentResponse(
        Long id,
        String email,
        String name,
        String phone,
        Boolean active,
        Long openCount,
        Long deliveredCount,
        Long failedCount,
        String joinedAt,
        String updatedAt
) {

    /**
     * Build a response with delivery statistics.
     */
    public static AgentResponse from(User user, long openCount, long deliveredCount, long failedCount) {
        return new AgentResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getIsActive(),
                openCount,
                deliveredCount,
                failedCount,
                TimeUtils.toIso(user.getCreatedAt()),
                TimeUtils.toIso(user.getUpdatedAt())
        );
    }

    /**
     * Build a response without statistics (e.g. after create/update).
     */
    public static AgentResponse from(User user) {
        return from(user, 0L, 0L, 0L);
    }
}
