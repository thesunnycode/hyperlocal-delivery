package com.hyperlocal.delivery.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import com.hyperlocal.delivery.BaseIntegrationTest;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.repository.ShipmentRepository;

/**
 * Integration tests for analytics endpoints (task 23.3).
 * Runs against MySQL (native queries use TIMESTAMPDIFF).
 */
@ActiveProfiles("mysql-test")
class AnalyticsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ShipmentRepository shipmentRepository;

    private Business business;
    private User owner;
    private User agent;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        business = createAndSaveBusiness("Analytics Biz", "analyticsbiz@test.com");
        owner = createAndSaveOwner(business, "analyticsowner@test.com");
        agent = createAndSaveAgent(business, "analyticsagent@test.com");
        ownerToken = tokenFor(owner);

        // Seed fixture data
        for (int i = 0; i < 5; i++) {
            Shipment shipment = Shipment.builder()
                    .trackingToken("analytics-track-" + i)
                    .business(business)
                    .assignedAgent(agent)
                    .status(i < 3 ? ShipmentStatus.DELIVERED : ShipmentStatus.ASSIGNED)
                    .customerName("Customer " + i)
                    .customerPhone("+91-900000000" + i)
                    .deliveryAddress(i + " Analytics St")
                    .build();
            shipmentRepository.save(shipment);
        }
    }

    @Test
    void overview_returnsMetrics() throws Exception {
        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.totalShipments").isNumber())
                .andExpect(jsonPath("$.data.delivered").isNumber());
    }

    @Test
    void agents_returnsPerAgentStats() throws Exception {
        mockMvc.perform(get("/api/analytics/agents")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void trend_returnsDailyVolume() throws Exception {
        mockMvc.perform(get("/api/analytics/shipments/trend")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void trend_rangeExceeds365Days_returns400() throws Exception {
        LocalDate from = LocalDate.now().minusDays(400);
        LocalDate to = LocalDate.now();

        mockMvc.perform(get("/api/analytics/shipments/trend")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overview_fromAfterTo_returns400() throws Exception {
        LocalDate from = LocalDate.now();
        LocalDate to = LocalDate.now().minusDays(10);

        mockMvc.perform(get("/api/analytics/overview")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }
}
