package com.hyperlocal.delivery.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hyperlocal.delivery.BaseIntegrationTest;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.User;

/**
 * Integration test for atomic delivery attempt recording (task 19.5).
 * Drives shipment to OUT_FOR_DELIVERY, then POSTs an attempt.
 * Verifies: delivery_attempts row, shipment status FAILED, event recorded.
 */
class DeliveryAttemptIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private Business business;
    private User owner;
    private User agent;
    private User otherAgent;
    private String ownerToken;
    private String agentToken;
    private String otherAgentToken;

    @BeforeEach
    void setUp() {
        business = createAndSaveBusiness("Attempt Biz", "attemptbiz@test.com");
        owner = createAndSaveOwner(business, "attemptowner@test.com");
        agent = createAndSaveAgent(business, "attemptagent@test.com");
        otherAgent = createAndSaveAgent(business, "attemptotheragent@test.com");
        ownerToken = tokenFor(owner);
        agentToken = tokenFor(agent);
        otherAgentToken = tokenFor(otherAgent);
    }

    @Test
    void recordAttempt_atomicTransition() throws Exception {
        // Create shipment
        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerName": "Attempt Customer",
                                    "customerPhone": "+91-9000000003",
                                    "deliveryAddress": "321 Attempt Lane"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long shipmentId = createResponse.get("data").get("id").asLong();

        // Drive to OUT_FOR_DELIVERY
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/start-transit")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/out-for-delivery")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());

        // Record delivery attempt
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/attempt")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"failureReason": "ADDRESS_NOT_FOUND", "notes": "Could not locate address"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attemptNumber").value(1))
                .andExpect(jsonPath("$.data.failureReason").value("Address not found"))
                .andExpect(jsonPath("$.data.shipmentId").value(shipmentId));

        // Verify shipment is now FAILED and has the FAILED event
        flushAndClear();
        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.events[?(@.toStatus == 'failed')]").isNotEmpty());

        // Verify delivery attempts list
        mockMvc.perform(get("/api/shipments/" + shipmentId + "/attempts")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].attemptNumber").value(1))
                .andExpect(jsonPath("$.data[0].failureReason").value("Address not found"));
    }

    /**
     * Task 5: the frontend sends/expects human-readable failure-reason
     * labels (e.g. "Address not found"), not the raw enum name
     * (ADDRESS_NOT_FOUND). Verifies both directions: the request body can
     * use the frontend label, and the response echoes the label back —
     * not the enum's .name().
     */
    @Test
    void recordAttempt_acceptsAndReturnsFrontendLabel() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerName": "Label Customer",
                                    "customerPhone": "+91-9000000005",
                                    "deliveryAddress": "5 Label Lane"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long shipmentId = createResponse.get("data").get("id").asLong();

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/start-transit")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/out-for-delivery")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/attempt")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"failureReason": "Address not found", "notes": "Could not locate address"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.failureReason").value("Address not found"));

        mockMvc.perform(get("/api/shipments/" + shipmentId + "/attempts")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].failureReason").value("Address not found"));
    }

    /**
     * Finding #2/#6: an agent who is NOT the assigned agent gets 404 (not
     * 403) when recording an attempt, matching the other five action
     * endpoints — otherwise 403-vs-404 would be a working existence oracle
     * for shipments across tenants.
     */
    @Test
    void recordAttempt_notAssignedAgent_returns404() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerName": "Attempt Customer 2",
                                    "customerPhone": "+91-9000000004",
                                    "deliveryAddress": "654 Attempt Lane"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long shipmentId = createResponse.get("data").get("id").asLong();

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/start-transit")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/out-for-delivery")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/attempt")
                        .header("Authorization", "Bearer " + otherAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"failureReason": "ADDRESS_NOT_FOUND", "notes": "Not my shipment"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void listAttempts_assignedAgent_canReadOwnShipment() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerName": "Agent Read Customer",
                                    "customerPhone": "+91-9000000004",
                                    "deliveryAddress": "4 Agent Read Lane"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long shipmentId = createResponse.get("data").get("id").asLong();

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/start-transit")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/out-for-delivery")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/attempt")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"failureReason": "REFUSED", "notes": "Customer refused"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/shipments/" + shipmentId + "/attempts")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].failureReason").value("Refused"));
    }

    /**
     * Flow audit 2026-09-10 (docs/audits/2026-09-10-flow-delivery-attempts.md):
     * the 3rd failed attempt must return the shipment to the store
     * (RETURNED), matching the agent app's own "After the third, it goes
     * back to the store." copy — previously every attempt just set FAILED
     * with no cap at all.
     */
    @Test
    void recordAttempt_thirdFailure_transitionsToReturned() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerName": "Three Strikes Customer",
                                    "customerPhone": "+91-9000000007",
                                    "deliveryAddress": "7 Three Strikes Lane"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long shipmentId = createResponse.get("data").get("id").asLong();

        for (int i = 1; i <= 2; i++) {
            mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                            .header("Authorization", "Bearer " + agentToken))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/shipments/" + shipmentId + "/start-transit")
                            .header("Authorization", "Bearer " + agentToken))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/shipments/" + shipmentId + "/out-for-delivery")
                            .header("Authorization", "Bearer " + agentToken))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/shipments/" + shipmentId + "/attempt")
                            .header("Authorization", "Bearer " + agentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"failureReason": "CUSTOMER_ABSENT", "notes": "Nobody home"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.attemptNumber").value(i));

            flushAndClear();
            mockMvc.perform(get("/api/shipments/" + shipmentId)
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(jsonPath("$.data.status").value("failed"));

            mockMvc.perform(post("/api/shipments/" + shipmentId + "/reassign")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"notes": "Try again"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("assigned"));
        }

        // Third attempt: must return the shipment to the store, not FAILED.
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/start-transit")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/out-for-delivery")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/attempt")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"failureReason": "CUSTOMER_ABSENT", "notes": "Nobody home"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attemptNumber").value(3));

        flushAndClear();
        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.data.status").value("returned"))
                .andExpect(jsonPath("$.data.events[?(@.toStatus == 'returned')]").isNotEmpty());

        // A terminal RETURNED shipment can no longer be reassigned or attempted.
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/reassign")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes": "One more try"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listAttempts_differentBusinessOwner_returns404() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerName": "Cross Tenant Customer",
                                    "customerPhone": "+91-9000000006",
                                    "deliveryAddress": "6 Cross Tenant Lane"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long shipmentId = createResponse.get("data").get("id").asLong();

        Business otherBusiness = createAndSaveBusiness("Other Attempt Biz", "otherattemptbiz@test.com");
        User otherOwner = createAndSaveOwner(otherBusiness, "otherattemptowner@test.com");
        String otherOwnerToken = tokenFor(otherOwner);

        mockMvc.perform(get("/api/shipments/" + shipmentId + "/attempts")
                        .header("Authorization", "Bearer " + otherOwnerToken))
                .andExpect(status().isNotFound());
    }
}
