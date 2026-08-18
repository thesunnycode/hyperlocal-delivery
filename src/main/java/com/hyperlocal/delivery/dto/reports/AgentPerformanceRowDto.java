package com.hyperlocal.delivery.dto.reports;

import com.hyperlocal.delivery.dto.analytics.AgentStats;

/**
 * One agent-performance row reshaped for the frontend's rider-performance report ({@code /owner/reports/agents}).
 *
 * <p>{@code open} (shipments still in progress within the range) is derived
 * as {@code assigned - delivered - failed - returned - cancelled}.
 * Assigned counts all shipments currently assigned to this agent and created
 * in the window. Open counts only ASSIGNED, PICKED_UP, IN_TRANSIT and
 * OUT_FOR_DELIVERY; FAILED is retryable but is not currently in progress.
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
        long open = stats.getTotalAssigned() - stats.getTotalDelivered() - stats.getTotalFailed() - stats.getTotalReturned() - stats.getTotalCancelled();
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
