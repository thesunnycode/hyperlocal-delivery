package com.hyperlocal.delivery.dto.agent;

import com.hyperlocal.delivery.model.User;

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
        String createdAt
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
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
        );
    }
}
