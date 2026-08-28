package com.hyperlocal.delivery.dto.reports;

import com.hyperlocal.delivery.dto.analytics.AgentStats;

/**
 * One agent-performance row reshaped for {@code AdminAgentPerformancePage.jsx}.
 *
 * <p>{@code open} (shipments still in progress within the range) is derived
 * as {@code assigned - delivered - failed - returned} rather than a new
 * query: {@code assigned} already counts every shipment touching the agent
 * in the window, and the three terminal-state counts are mutually
 * exclusive subsets of it, so the remainder is exactly the non-terminal
 * ("open") count for that window.
 */
public record AgentPerformanceRowDto(
        Long id,
        String name,
        boolean active,
        long assigned,
        long delivered,
        long failed,
        long returned,
        long open,
        double avgHours,
        double perDay
) {

    public static AgentPerformanceRowDto from(AgentStats stats) {
        long open = stats.getTotalAssigned() - stats.getTotalDelivered() - stats.getTotalFailed() - stats.getTotalReturned();
        double avgHours = stats.getAvgDeliveryTimeHours() != null ? stats.getAvgDeliveryTimeHours() : 0.0;
        double perDay = stats.getAvgShipmentsPerDay() != null ? stats.getAvgShipmentsPerDay() : 0.0;
        boolean active = stats.getActive() != null && stats.getActive();
        return new AgentPerformanceRowDto(
                stats.getAgentId(),
                stats.getAgentName(),
                active,
                stats.getTotalAssigned(),
                stats.getTotalDelivered(),
                stats.getTotalFailed(),
                stats.getTotalReturned(),
                Math.max(open, 0L),
                avgHours,
                perDay
        );
    }
}
