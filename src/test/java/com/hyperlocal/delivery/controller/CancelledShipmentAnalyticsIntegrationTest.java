package com.hyperlocal.delivery.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import com.hyperlocal.delivery.BaseIntegrationTest;
import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.repository.ShipmentRepository;

@ActiveProfiles("mysql-test")
class CancelledShipmentAnalyticsIntegrationTest extends BaseIntegrationTest {
    @Autowired private ShipmentRepository shipments;
    private String token;

    @BeforeEach
    void seedEveryStatus() {
        var business = createAndSaveBusiness("Status metrics", "status-biz@test.com");
        token = tokenFor(createAndSaveOwner(business, "status-owner@test.com"));
        var agent = createAndSaveAgent(business, "status-agent@test.com");
        for (var status : ShipmentStatus.values()) {
            shipments.save(Shipment.builder().business(business).assignedAgent(agent)
                    .trackingToken("status-metric-" + status).status(status)
                    .customerName("Status fixture").customerPhone("9000000000")
                    .deliveryAddress("1 Test Street").build());
        }
        flushAndClear();
    }

    @Test
    void overviewKeepsCancelledInTotalButNotInProgress() throws Exception {
        mockMvc.perform(get("/api/analytics/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalShipments").value(8))
                .andExpect(jsonPath("$.data.inProgress").value(4));
    }

    @Test
    void trendKeepsCancelledInTotalButNotInProgress() throws Exception {
        mockMvc.perform(get("/api/analytics/shipments/trend").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].total").value(8))
                .andExpect(jsonPath("$.data[0].inProgress").value(4));
    }

    @Test
    void agentReportKeepsCancelledInAssignedButNotOpen() throws Exception {
        mockMvc.perform(get("/api/reports/agent-performance?range=30").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].assigned").value(8))
                .andExpect(jsonPath("$.data[0].open").value(4));
    }
}
