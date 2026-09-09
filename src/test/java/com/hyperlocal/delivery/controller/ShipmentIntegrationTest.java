package com.hyperlocal.delivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Integration tests for the full shipment lifecycle (tasks 17.9, 17.10, 17.11).
 */
class ShipmentIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private Business business;
    private User owner;
    private User agent;
    private String ownerToken;
    private String agentToken;

    @BeforeEach
    void setUp() {
        business = createAndSaveBusiness("Ship Biz", "shipbiz@test.com");
        owner = createAndSaveOwner(business, "shipowner@test.com");
        agent = createAndSaveAgent(business, "shipagent@test.com");
        ownerToken = tokenFor(owner);
        agentToken = tokenFor(agent);
    }

    /**
     * 17.9 — Full shipment happy path:
     * create → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED
     */
    @Test
    void fullShipmentHappyPath() throws Exception {
        // Create shipment (auto-assigns to agent)
        String createBody = """
                {
                    "customerName": "Happy Customer",
                    "customerPhone": "+91-9000000001",
                    "deliveryAddress": "456 Happy Street"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("assigned"))
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long shipmentId = createResponse.get("data").get("id").asLong();

        // Transition: ASSIGNED → PICKED_UP (agent)
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Picked up from warehouse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("picked_up"));

        // Transition: PICKED_UP → IN_TRANSIT (agent)
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/start-transit")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"On the way\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("in_transit"));

        // Transition: IN_TRANSIT → OUT_FOR_DELIVERY (agent)
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/out-for-delivery")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Almost there\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("out_for_delivery"));

        // Transition: OUT_FOR_DELIVERY → DELIVERED (agent)
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/deliver")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Delivered to customer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("delivered"))
                .andExpect(jsonPath("$.data.deliveredAt").isNotEmpty());

        // Verify event trail via GET shipment detail
        // Flush and clear the persistence context so the entity graph fetch
        // actually loads the events from the database
        flushAndClear();

        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events", hasSize(5)))  // ASSIGNED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED
                .andExpect(jsonPath("$.data.events[0].toStatus").value("assigned"))
                .andExpect(jsonPath("$.data.events[1].toStatus").value("picked_up"))
                .andExpect(jsonPath("$.data.events[2].toStatus").value("in_transit"))
                .andExpect(jsonPath("$.data.events[3].toStatus").value("out_for_delivery"))
                .andExpect(jsonPath("$.data.events[4].toStatus").value("delivered"));
    }

    /**
     * 17.10 — Reassign after FAILED:
     * Drive to OUT_FOR_DELIVERY → FAILED (via attempt), then reassign.
     */
    @Test
    void reassignAfterFailed() throws Exception {
        // Create shipment
        String createBody = """
                {
                    "customerName": "Fail Customer",
                    "customerPhone": "+91-9000000002",
                    "deliveryAddress": "789 Fail Street"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
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

        // Record a failed delivery attempt → transitions to FAILED
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/attempt")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"failureReason": "CUSTOMER_ABSENT", "notes": "Nobody home"}
                                """))
                .andExpect(status().isCreated());

        // Verify shipment is now FAILED
        flushAndClear();
        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.data.status").value("failed"));

        // Reassign (auto-assignment)
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/reassign")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes": "Reassigning to try again"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("assigned"));

        // Verify event trail includes the FAILED→ASSIGNED transition
        flushAndClear();
        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[?(@.toStatus == 'assigned')]").isNotEmpty());
    }

    /**
     * Task 10 — ShipmentEventDto must expose the superset of fields read by
     * all three frontend pages: owner/agent read {toStatus||status, label,
     * meta}, customer tracking reads {status, label, stamp}, admin register
     * inspect reads {time, label}.
     */
    @Test
    void eventDtoIncludesSupersetOfFieldsAllFrontendPagesRead() throws Exception {
        String createBody = """
                {
                    "customerName": "Superset Customer",
                    "customerPhone": "+91-9000000030",
                    "deliveryAddress": "30 Superset Street"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long shipmentId = createResponse.get("data").get("id").asLong();

        flushAndClear();

        MvcResult detailResult = mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[0].status").exists())
                .andExpect(jsonPath("$.data.events[0].toStatus").exists())
                .andExpect(jsonPath("$.data.events[0].label").exists())
                .andExpect(jsonPath("$.data.events[0].meta").exists())
                .andExpect(jsonPath("$.data.events[0].stamp").exists())
                .andExpect(jsonPath("$.data.events[0].time").exists())
                .andReturn();

        JsonNode detail = objectMapper.readTree(detailResult.getResponse().getContentAsString());
        JsonNode event0 = detail.get("data").get("events").get(0);
        assertThat(event0.get("status").asString()).isEqualTo(event0.get("toStatus").asString());
        assertThat(event0.get("label").asString()).isEqualTo("Assigned");
    }

    /**
     * Task 10 — DeliveryAttemptDto exposed via the shared shipment-detail
     * "attempts" array must use the singular "note" field
     * (AttemptLogList.jsx reads a.note, never a.notes), plus "no" and
     * "reason" as read by owner, agent, and admin pages.
     */
    @Test
    void attemptDtoUsesSingularNoteFieldNotNotes() throws Exception {
        String createBody = """
                {
                    "customerName": "Attempt Note Customer",
                    "customerPhone": "+91-9000000031",
                    "deliveryAddress": "31 Attempt Street"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
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
                                {"failureReason": "CUSTOMER_ABSENT", "notes": "no answer"}
                                """))
                .andExpect(status().isCreated());

        flushAndClear();

        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempts[0].no").value(1))
                .andExpect(jsonPath("$.data.attempts[0].stamp").exists())
                .andExpect(jsonPath("$.data.attempts[0].reason").value("Customer absent"))
                .andExpect(jsonPath("$.data.attempts[0].note").value("no answer"))
                .andExpect(jsonPath("$.data.attempts[0].notes").doesNotExist());
    }

    /**
     * 17.11 — Route ordering: GET /api/shipments/mine with agent JWT
     * returns 200 (not 400/404 from being matched as {id}).
     */
    @Test
    void myAssignments_routeOrdering_returns200() throws Exception {
        mockMvc.perform(get("/api/shipments/mine")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    /**
     * Confirms the frontend's real query param name (`status`, not the old
     * `filter`) is the one the controller binds
     * ({@link com.hyperlocal.delivery.controller.ShipmentController#myAssignments})
     * and that its wire-format values ('assigned', 'in_transit') round-trip
     * correctly through {@code ShipmentStatusConverter} to filter the agent's
     * own queue to a single status.
     */
    @Test
    void myAssignments_statusQueryParam_filtersToSingleStatus() throws Exception {
        long assignedShipmentId = createShipmentForAgent("Still Assigned Customer");
        long inTransitShipmentId = createShipmentForAgent("In Transit Customer");

        mockMvc.perform(post("/api/shipments/" + inTransitShipmentId + "/pickup")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Picked up\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + inTransitShipmentId + "/start-transit")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"On the road\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/shipments/mine")
                        .param("status", "in_transit")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(inTransitShipmentId))
                .andExpect(jsonPath("$.data[0].status").value("in_transit"));

        mockMvc.perform(get("/api/shipments/mine")
                        .param("status", "assigned")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(assignedShipmentId))
                .andExpect(jsonPath("$.data[0].status").value("assigned"));
    }

    private long createShipmentForAgent(String customerName) throws Exception {
        String createBody = """
                {
                    "customerName": "%s",
                    "customerPhone": "+91-9000000002",
                    "deliveryAddress": "789 Queue Street"
                }
                """.formatted(customerName);

        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        return createResponse.get("data").get("id").asLong();
    }

    /**
     * Owner mutation #1: reassign the agent on a non-terminal, non-FAILED
     * shipment. Status must not change.
     */
    @Test
    void reassign_nonTerminalShipment_changesAgentOnlyNoStatusChange() throws Exception {
        User secondAgent = createAndSaveAgent(business, "secondagent@test.com");

        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerName": "Reassign Customer",
                                    "customerPhone": "+91-9000000020",
                                    "deliveryAddress": "20 Reassign Street"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long shipmentId = createResponse.get("data").get("id").asLong();

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/reassign")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\": " + secondAgent.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("assigned"))
                .andExpect(jsonPath("$.data.agentId").value(secondAgent.getId()));
    }

    /**
     * A terminal shipment can never be reassigned.
     */
    @Test
    void reassign_terminalShipment_rejectedWith422() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerName": "Terminal Customer",
                                    "customerPhone": "+91-9000000021",
                                    "deliveryAddress": "21 Terminal Street"
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
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/deliver")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/reassign")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    /**
     * Task 18 regression: the NON_TERMINAL set used by reassign covers both
     * terminal statuses. The test above only drives a shipment to DELIVERED;
     * this one drives to RETURNED (the other terminal status) to make sure
     * that branch is rejected too, not just DELIVERED.
     */
    @Test
    void reassign_returnedShipment_rejectedWith422() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerName": "Returned Customer",
                                    "customerPhone": "+91-9000000022",
                                    "deliveryAddress": "22 Returned Street"
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
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/return")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/reassign")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    /**
     * Bug found via manual E2E verification: the real frontend
     * (shipmentsApi.js createShipment) sends {@code {customerName,
     * customerPhone, address, scheduledAt}} — note "address", not
     * "deliveryAddress". CreateShipmentRequest must accept that exact shape
     * via @JsonAlias (same precedent as note/notes and fullName/name).
     */
    @Test
    void create_acceptsRealFrontendRequestShape_withAddressAndScheduledAtKeys() throws Exception {
        String createBody = """
                {
                    "customerName": "Frontend Shape Customer",
                    "customerPhone": "+91-9000000040",
                    "address": "40 Frontend Shape Street",
                    "scheduledAt": "2030-01-01T10:00:00"
                }
                """;

        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("assigned"))
                .andExpect(jsonPath("$.data.address").value("40 Frontend Shape Street"))
                .andExpect(jsonPath("$.data.scheduledAt").exists());
    }

    /**
     * Bug found via manual E2E verification: OwnerShipmentsPage.jsx reads
     * created.id / created.agentName on create, and selected.token /
     * selected.address / selected.scheduledAt / selected.agentName /
     * selected.agentId on the detail view — not trackingToken /
     * deliveryAddress / scheduledDeliveryAt / a nested assignedAgent object.
     * ShipmentResponseDto must expose the flat frontend-contract field names.
     */
    @Test
    void create_and_get_returnFrontendContractResponseFields() throws Exception {
        String createBody = """
                {
                    "customerName": "Response Shape Customer",
                    "customerPhone": "+91-9000000041",
                    "address": "41 Response Shape Street",
                    "scheduledAt": "2030-01-01T10:00:00"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.address").value("41 Response Shape Street"))
                .andExpect(jsonPath("$.data.scheduledAt").exists())
                .andExpect(jsonPath("$.data.agentId").exists())
                .andExpect(jsonPath("$.data.agentName").value(agent.getFullName()))
                .andExpect(jsonPath("$.data.trackingToken").doesNotExist())
                .andExpect(jsonPath("$.data.deliveryAddress").doesNotExist())
                .andExpect(jsonPath("$.data.scheduledDeliveryAt").doesNotExist())
                .andExpect(jsonPath("$.data.assignedAgent").doesNotExist())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long shipmentId = createResponse.get("data").get("id").asLong();

        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.address").value("41 Response Shape Street"))
                .andExpect(jsonPath("$.data.customerPhone").exists())
                .andExpect(jsonPath("$.data.scheduledAt").exists())
                .andExpect(jsonPath("$.data.agentId").exists())
                .andExpect(jsonPath("$.data.agentName").value(agent.getFullName()))
                .andExpect(jsonPath("$.data.events").exists())
                .andExpect(jsonPath("$.data.attempts").exists());
    }

    /**
     * Bug found via manual E2E verification: OwnerShipmentsPage.jsx's list
     * rows read s.token / s.customerName / s.scheduledAt / s.agentName —
     * ShipmentSummaryDto must expose those flat field names too, not
     * trackingToken / scheduledDeliveryAt / a nested assignedAgent object.
     */
    @Test
    void list_returnsFrontendContractSummaryFields() throws Exception {
        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerName": "List Shape Customer",
                                    "customerPhone": "+91-9000000042",
                                    "address": "42 List Shape Street",
                                    "scheduledAt": "2030-01-01T10:00:00"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].token").exists())
                .andExpect(jsonPath("$.data[0].customerName").exists())
                .andExpect(jsonPath("$.data[0].scheduledAt").exists())
                .andExpect(jsonPath("$.data[0].agentName").value(agent.getFullName()))
                .andExpect(jsonPath("$.data[0].trackingToken").doesNotExist())
                .andExpect(jsonPath("$.data[0].scheduledDeliveryAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].assignedAgent").doesNotExist());
    }

    /**
     * Task 4: GET /api/shipments?status=picked_up (lowercase snake_case) must
     * bind via the registered Converter<String, ShipmentStatus>, and the
     * returned status field must itself be lowercase snake_case (real
     * end-to-end response, not just the enum-level Jackson test).
     */
    @Test
    void listShipments_filterByLowercaseStatusQueryParam_andStatusFieldIsLowercase() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerName": "Query Param Customer",
                                    "customerPhone": "+91-9000000030",
                                    "deliveryAddress": "30 Query Param Street"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("assigned"))
                .andReturn();
        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long shipmentId = createResponse.get("data").get("id").asLong();

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("picked_up"));

        flushAndClear();

        // Lowercase query param must bind to ShipmentStatus.PICKED_UP and filter correctly.
        mockMvc.perform(get("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("status", "picked_up"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + shipmentId + ")].status").value(hasItem("picked_up")));

        // A different lowercase status must exclude this shipment.
        mockMvc.perform(get("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("status", "delivered"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + shipmentId + ")]").isEmpty());
    }
}
