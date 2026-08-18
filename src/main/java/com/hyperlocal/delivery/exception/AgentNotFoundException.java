package com.hyperlocal.delivery.exception;

/**
 * Thrown when a delivery agent cannot be found within the caller's tenant.
 */
public class AgentNotFoundException extends DomainException {

    public AgentNotFoundException(String message) {
        super(ErrorCode.AGENT_NOT_FOUND, message);
    }

    public AgentNotFoundException(Long agentId) {
        super(ErrorCode.AGENT_NOT_FOUND, "Agent not found: " + agentId);
    }
}
