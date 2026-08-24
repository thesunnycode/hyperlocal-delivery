package com.hyperlocal.delivery.dto.shipment;

import com.hyperlocal.delivery.model.User;

/**
 * Minimal agent projection embedded in shipment responses.
 */
public record AgentMiniDto(Long id, String fullName, String phone) {

    /**
     * Build from a user entity; returns null if the agent is null.
     */
    public static AgentMiniDto from(User agent) {
        return agent == null ? null : new AgentMiniDto(agent.getId(), agent.getFullName(), agent.getPhone());
    }
}
