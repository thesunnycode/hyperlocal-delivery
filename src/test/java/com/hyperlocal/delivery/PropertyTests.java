package com.hyperlocal.delivery;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.User;

/**
 * Cross-cutting property tests: invariants that must hold across every
 * feature rather than within one endpoint — tenant isolation, refresh-token
 * one-shot use, audit-trail completeness, and response-envelope shape.
 *
 * <p>Example-based, not randomized: see docs/phase-08-testing/
 * task-17-write-property-based-tests.md for why this project doesn't use jqwik.
 */
class PropertyTests extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Business businessA;
    private Business businessB;
    private User ownerA;
    private User ownerB;
    private User agentA;
    private User agentB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        businessA = createAndSaveBusiness("Business A", "biza@test.com");
        businessB = createAndSaveBusiness("Business B", "bizb@test.com");
        ownerA = createAndSaveOwner(businessA, "ownera@test.com");
        ownerB = createAndSaveOwner(businessB, "ownerb@test.com");
        agentA = createAndSaveAgent(businessA, "agenta@test.com");
        agentB = createAndSaveAgent(businessB, "agentb@test.com");
        tokenA = tokenFor(ownerA);
        tokenB = tokenFor(ownerB);
    }

    /**
     * 26.1 — Tenant isolation: Business A can't see Business B's agents/shipments.
     */
    @Test
    void tenantIsolation_businessA_cannotSeeBusinessB_agents() throws Exception {
        // Business A tries to get Business B's agent → 404
        mockMvc.perform(get("/api/agents/" + agentB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // Business B tries to get Business A's agent → 404
        mockMvc.perform(get("/api/agents/" + agentA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantIsolation_businessA_cannotSeeBusinessB_shipments() throws Exception {
        // Create a shipment in Business A
        MvcResult result = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerName": "Customer A",
                                    "customerPhone": "+91-9000000001",
                                    "deliveryAddress": "1 A Street"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        long shipmentId = response.get("data").get("id").asLong();

        // Business B tries to get Business A's shipment → 404
        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    /**
     * 26.2 — Refresh token one-shot: Login, refresh, verify old token rejected.
     */
    @Test
    void refreshToken_oneShot_oldTokenRejected() throws Exception {
        // Create a user with a known password via the service layer
        Business biz = createAndSaveBusiness("OneShot Biz", "oneshotbiz@test.com");
        User owner = User.builder()
                .business(biz)
                .email("oneshot@test.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(com.hyperlocal.delivery.model.UserRole.BUSINESS_OWNER)
                .fullName("OneShot Owner")
                .phone("+91-9000000099")
                .build();
        userRepository.save(owner);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "oneshot@test.com",
                                    "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginResponse = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String actualRefresh = loginResponse.get("data").get("refreshToken").asString();

        // First refresh — should succeed
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + actualRefresh + "\"}"))
                .andExpect(status().isOk());

        // Second refresh with the SAME old token — should fail (token consumed)
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + actualRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 26.3 — Audit immutability: Drive transitions, verify event count matches.
     */
    @Test
    void auditImmutability_eventCountMatchesTransitions() throws Exception {
        String agentToken = tokenFor(agentA);

        // Create shipment
        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerName": "Audit Customer",
                                    "customerPhone": "+91-9000000005",
                                    "deliveryAddress": "5 Audit Lane"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long shipmentId = createResponse.get("data").get("id").asLong();

        // Drive through transitions: PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED
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

        // Verify event count: ASSIGNED (genesis) + 4 transitions = 5 events
        flushAndClear();
        mockMvc.perform(get("/api/shipments/" + shipmentId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events", hasSize(5)));
    }

    /**
     * 26.4 — Response envelope conformance: success and error responses
     * match the expected JSON structure.
     */
    @Test
    void responseEnvelope_success_hasCorrectStructure() throws Exception {
        // Success response: GET agents
        mockMvc.perform(get("/api/agents")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void responseEnvelope_error_hasCorrectStructure() throws Exception {
        // Error response: GET non-existent agent
        mockMvc.perform(get("/api/agents/99999")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists());
    }
}
