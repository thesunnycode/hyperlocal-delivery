package com.hyperlocal.delivery.exception;

/**
 * Thrown when deactivating an agent that still owns active shipments.
 * Callers must reassign those shipments before the agent can be
 * deactivated.
 */
public class AgentHasActiveShipmentsException extends DomainException {

    public AgentHasActiveShipmentsException(long count) {
        super(
                ErrorCode.AGENT_HAS_ACTIVE_SHIPMENTS,
                "Rider has " + count + " active shipment(s); reassign before deactivating");
    }
}
