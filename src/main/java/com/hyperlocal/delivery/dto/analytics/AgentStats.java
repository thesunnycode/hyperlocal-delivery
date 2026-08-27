package com.hyperlocal.delivery.dto.analytics;

import lombok.Getter;

/**
 * Per-agent performance metrics. Used as a JPQL constructor expression
 * target — the constructor signature must match the SELECT clause exactly.
 *
 * <p>{@code avgShipmentsPerDay} is computed in the service layer after
 * the JPQL query returns.
 */
@Getter
public class AgentStats {

    private final Long agentId;
    private final String agentName;
    private final long totalAssigned;
    private final long totalDelivered;
    private final long totalFailed;
    private final long totalReturned;
    private final Double avgDeliveryTimeHours;
    private final Boolean active;
    private Double avgShipmentsPerDay;

    /**
     * Constructor matching the JPQL {@code SELECT new ...} expression.
     */
    public AgentStats(Long agentId, String agentName, long totalAssigned,
                      long totalDelivered, long totalFailed, long totalReturned,
                      Double avgDeliveryTimeHours, Boolean active) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.totalAssigned = totalAssigned;
        this.totalDelivered = totalDelivered;
        this.totalFailed = totalFailed;
        this.totalReturned = totalReturned;
        this.avgDeliveryTimeHours = avgDeliveryTimeHours;
        this.active = active;
        this.avgShipmentsPerDay = null;
    }

    /**
     * Returns a copy with the computed average shipments per day.
     */
    public AgentStats withAvgShipmentsPerDay(Double avg) {
        this.avgShipmentsPerDay = avg;
        return this;
    }
}
