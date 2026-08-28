package com.hyperlocal.delivery.dto.reports;

/**
 * One row of the overview's "Agents &middot; delivered" mini-chart.
 * {@code pct} is the bar width relative to the top agent in the list shown
 * (not a share of total business volume) so the bars read as a comparison
 * among the displayed agents.
 */
public record AgentBarDto(String name, long delivered, double pct) {
}
