package com.hyperlocal.delivery.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hyperlocal.delivery.BaseIntegrationTest;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.DeliveryAttempt;
import com.hyperlocal.delivery.model.FailureReason;
import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.repository.ShipmentRepository;

/**
 * Integration tests for the explicit per-action agent endpoints
 * (pickup / transit / out-for-delivery / deliver / return), replacing the
 * generic status endpoint. Covers the happy path, the wrong-state rejection,
 * and the agent-scoping fix (an agent cannot act on another agent's shipment).
 */
class ShipmentActionEndpointsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShipmentRepository shipmentRepository;

    private Business business;
    private User owner;
    private User agent;
    private User otherAgent;
    private String ownerToken;
    private String agentToken;
    private String otherAgentToken;

    @BeforeEach
    void setUp() {
        business = createAndSaveBusiness("Action Biz", "actionbiz@test.com");
        owner = createAndSaveOwner(business, "actionowner@test.com");
        agent = createAndSaveAgent(business, "actionagent@test.com");
        otherAgent = createAndSaveAgent(business, "otheragent@test.com");
        ownerToken = tokenFor(owner);
        agentToken = tokenFor(agent);
        otherAgentToken = tokenFor(otherAgent);
    }

    private long createShipmentAssignedTo(User targetAgent) throws Exception {
        // create() auto-assigns to whichever active agent has the fewest open
        // shipments; with two fresh agents the first shipment goes to agent
        // (lower id, tiebreaker). We only ever call this once per test with
        // a single pre-existing shipment, so the assignment is deterministic.
        MvcResult result = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerName": "Action Customer",
                                    "customerPhone": "+91-9000000010",
                                    "deliveryAddress": "10 Action Street"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("data").get("id").asLong();
    }

    @Test
    void pickup_happyPath_movesToPickedUp() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Picked up from warehouse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("picked_up"));
    }

    @Test
    void pickup_wrongState_rejectedWith422() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);

        // First pickup succeeds, moving the shipment to PICKED_UP...
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());

        // ...a second pickup call is now the wrong state (ASSIGNED required).
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void pickup_wrongAgent_returns404_notForbidden() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);

        // Regression test for the agent-scoping bug: otherAgent is a real
        // agent in the same business, but is NOT assigned to this shipment.
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + otherAgentToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void transit_happyPath_movesToInTransit() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/start-transit")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("in_transit"));
    }

    @Test
    void outForDelivery_happyPath_movesToOutForDelivery() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/start-transit")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/out-for-delivery")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("out_for_delivery"));
    }

    private long driveToOutForDelivery(String agentToken, long shipmentId) throws Exception {
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/start-transit")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/out-for-delivery")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        return shipmentId;
    }

    @Test
    void deliver_happyPath_movesToDeliveredWithTimestamp() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);
        driveToOutForDelivery(agentToken, shipmentId);

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/deliver")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("delivered"))
                .andExpect(jsonPath("$.data.deliveredAt").isNotEmpty());
    }

    @Test
    void returnShipment_happyPath_movesToReturned() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);
        driveToOutForDelivery(agentToken, shipmentId);

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/return")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("returned"));
    }

    /**
     * Task 8: frontend calls {@code POST /{id}/fail} (logFailedAttempt),
     * which must transition OUT_FOR_DELIVERY -> FAILED and append exactly
     * one immutable DeliveryAttempt row.
     */
    @Test
    void failEndpointTransitionsToFailedAndAppendsAttempt() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);
        driveToOutForDelivery(agentToken, shipmentId);

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/fail")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Customer absent\",\"notes\":\"no answer after 3 tries\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("failed"));

        flushAndClear();
        Shipment reloaded = shipmentRepository.findById(shipmentId).orElseThrow();
        assertThat(reloaded.getAttempts()).hasSize(1);
        DeliveryAttempt attempt = reloaded.getAttempts().iterator().next();
        assertThat(attempt.getFailureReason()).isEqualTo(FailureReason.CUSTOMER_ABSENT);
        assertThat(attempt.getNotes()).isEqualTo("no answer after 3 tries");
    }

    @Test
    void failEndpointRejectsWrongAgentWith404NotFound() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);
        driveToOutForDelivery(agentToken, shipmentId);

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/fail")
                        .header("Authorization", "Bearer " + otherAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Other\",\"notes\":\"n/a\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deliver_wrongAgent_returns404() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);
        driveToOutForDelivery(agentToken, shipmentId);

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/deliver")
                        .header("Authorization", "Bearer " + otherAgentToken))
                .andExpect(status().isNotFound());
    }

    /**
     * The spec's central owner constraint (STATE_MACHINE.md: "The owner
     * cannot advance a shipment forward, cannot mark anything delivered")
     * rests entirely on the five @PreAuthorize annotations on these
     * endpoints. These two tests exercise that constraint directly rather
     * than trusting the annotation is present.
     */
    @Test
    void deliver_ownerToken_returns403() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);
        driveToOutForDelivery(agentToken, shipmentId);

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/deliver")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void pickup_ownerToken_returns403() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    /**
     * Task 9: {@code GET /{id}} is now shared between owners and agents,
     * branching internally on the caller's role instead of requiring agents
     * to call a separate {@code /my-detail} path.
     */
    @Test
    void agentDetailUsesSharedIdPathNotMyDetail() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);

        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(shipmentId))
                .andExpect(jsonPath("$.data.status").value("assigned"))
                .andExpect(jsonPath("$.data.events").isArray());
    }

    @Test
    void agentDetailOnUnassignedShipmentReturns404ViaSharedPath() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);

        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + otherAgentToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerDetailStillWorksViaSharedPath() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);

        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(shipmentId));
    }

    @Test
    void oldMyDetailPathNoLongerExists() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);

        mockMvc.perform(get("/api/shipments/" + shipmentId + "/my-detail")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isNotFound());
    }

    /**
     * Task 7: frontend calls {@code POST /{id}/start-transit}, not the
     * backend's old {@code /transit} path.
     */
    @Test
    void startTransitPathMatchesFrontendContract() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/start-transit")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    /**
     * Task 7: frontend calls {@code GET /mine}, not the backend's old
     * {@code /my-assignments} path.
     */
    @Test
    void mineListPathMatchesFrontendContract() throws Exception {
        mockMvc.perform(get("/api/shipments/mine")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
    }

    /**
     * Task 7: frontend calls {@code POST /{id}/reassign}, not the backend's
     * old {@code PUT}-only mapping.
     */
    @Test
    void reassignAcceptsPostNotPut() throws Exception {
        long shipmentId = createShipmentAssignedTo(agent);

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/reassign")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\": " + otherAgent.getId() + "}"))
                .andExpect(status().isOk());
    }
}
