package com.hyperlocal.delivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.hyperlocal.delivery.BaseIntegrationTest;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.repository.ShipmentRepository;

/**
 * Integration tests for {@link AgentController}.
 */
class AgentControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Business business;
    private User owner;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        business = createAndSaveBusiness("Test Biz", "biz@test.com");
        owner = createAndSaveOwner(business, "owner@test.com");
        ownerToken = tokenFor(owner);
    }

    @Test
    void createAgent_returns201() throws Exception {
        String body = """
                {
                    "fullName": "Agent One",
                    "email": "agent1@test.com",
                    "password": "password123",
                    "phone": "+91-9000000001"
                }
                """;

        mockMvc.perform(post("/api/agents")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.email").value("agent1@test.com"))
                .andExpect(jsonPath("$.data.name").value("Agent One"))
                .andExpect(jsonPath("$.data.phone").value("+91-9000000001"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    void createAgent_withNameFieldInsteadOfFullName_returns201() throws Exception {
        // Matches exactly what the real frontend sends: agentsApi.js's
        // createAgent({name, email, phone}) posts a "name" key, never
        // "fullName". CreateAgentRequest.fullName must accept it via
        // @JsonAlias, the same pattern already used for note/notes.
        String body = """
                {
                    "name": "Frontend Contract Agent",
                    "email": "frontend-contract-agent@test.com",
                    "phone": "+91-9000000010"
                }
                """;

        mockMvc.perform(post("/api/agents")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.name").value("Frontend Contract Agent"))
                .andExpect(jsonPath("$.data.email").value("frontend-contract-agent@test.com"))
                .andExpect(jsonPath("$.data.phone").value("+91-9000000010"));
    }

    @Test
    void createAgent_withoutPasswordField_returns201WithRandomHashedPassword() throws Exception {
        // Matches exactly what the real frontend sends: OwnerAgentsPage.jsx's
        // createAgent() only ever posts name/email/phone, never a password
        // field, because agents are onboarded via invite/reset-link, not by
        // the owner choosing their password.
        String body = """
                {
                    "fullName": "No Password Agent",
                    "email": "no-password-agent@test.com",
                    "phone": "+91-9000000009"
                }
                """;

        mockMvc.perform(post("/api/agents")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.email").value("no-password-agent@test.com"))
                .andExpect(jsonPath("$.data.active").value(true));

        User created = userRepository.findByEmailAndDeletedAtIsNull("no-password-agent@test.com")
                .orElseThrow();
        assertThat(created.getPasswordHash()).isNotNull();
        assertThat(created.getPasswordHash()).isNotBlank();
        // Never a predictable placeholder like "changeme" stored verbatim.
        assertThat(created.getPasswordHash()).isNotEqualTo("changeme");
        // Must be a real BCrypt hash, not the plaintext password itself.
        assertThat(created.getPasswordHash()).startsWith("$2");
        // The stored hash must not verify against an empty/blank password —
        // proving a real random secret was generated and hashed, not "".
        assertThat(passwordEncoder.matches("", created.getPasswordHash())).isFalse();
    }

    @Test
    void createAgent_withBlankPasswordField_returns201WithRandomHashedPassword() throws Exception {
        String body = """
                {
                    "fullName": "Blank Password Agent",
                    "email": "blank-password-agent@test.com",
                    "password": "",
                    "phone": "+91-9000000008"
                }
                """;

        mockMvc.perform(post("/api/agents")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        User created = userRepository.findByEmailAndDeletedAtIsNull("blank-password-agent@test.com")
                .orElseThrow();
        assertThat(created.getPasswordHash()).isNotNull().startsWith("$2");
    }

    @Test
    void createAgent_duplicateEmail_returns409() throws Exception {
        // Create an agent first
        createAndSaveAgent(business, "dup@test.com");

        String body = """
                {
                    "fullName": "Agent Dup",
                    "email": "dup@test.com",
                    "password": "password123",
                    "phone": "+91-9000000002"
                }
                """;

        mockMvc.perform(post("/api/agents")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void listAgents_returnsPaginatedResponse() throws Exception {
        createAndSaveAgent(business, "a1@test.com");
        createAndSaveAgent(business, "a2@test.com");

        mockMvc.perform(get("/api/agents")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination").exists());
    }

    @Test
    void listAgents_rowsUseFrontendContractFieldNames() throws Exception {
        // Matches exactly what OwnerAgentsPage.jsx / OwnerShipmentsPage.jsx
        // read off each list row: a.id, a.name, a.email, a.phone, a.active,
        // a.openCount — never the backend's old internal names
        // (fullName/isActive/activeShipments).
        createAndSaveAgent(business, "contract-list-agent@test.com");

        mockMvc.perform(get("/api/agents")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").isNumber())
                .andExpect(jsonPath("$.data[0].name").value("Test Agent"))
                .andExpect(jsonPath("$.data[0].email").value("contract-list-agent@test.com"))
                .andExpect(jsonPath("$.data[0].phone").exists())
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[0].openCount").value(0))
                .andExpect(jsonPath("$.data[0].fullName").doesNotExist())
                .andExpect(jsonPath("$.data[0].isActive").doesNotExist())
                .andExpect(jsonPath("$.data[0].activeShipments").doesNotExist());
    }

    @Test
    void listAgents_withActiveTrueQueryParam_excludesDeactivatedAgents() throws Exception {
        // Matches exactly what OwnerShipmentsPage.jsx sends to populate the
        // reassign-agent dropdown: agentsApi.js's listAgents({active: true})
        // serializes to "?active=true" (not "?isActive=true"), so the
        // controller's @RequestParam must be named "active" or this filter
        // silently never applies and deactivated agents leak into the list.
        User activeAgent = createAndSaveAgent(business, "active-agent@test.com");
        User inactiveAgent = createAndSaveAgent(business, "inactive-agent@test.com");
        mockMvc.perform(post("/api/agents/" + inactiveAgent.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/agents?active=true")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(activeAgent.getId()))
                .andExpect(jsonPath("$.data[0].active").value(true));
    }

    @Test
    void listAgents_withNoQueryParam_includesDeactivatedAgent() throws Exception {
        // Bug found via manual E2E verification: after deactivating an
        // agent ("Ravi Kumar") through the real app, clicking the "All"
        // filter tab on the Agents page — which calls listAgents({}),
        // i.e. GET /api/agents with no query params at all — returned
        // only the still-active agent. The deactivated agent vanished
        // from every list view with no way back to it short of guessing
        // its id and hitting GET /api/agents/{id} directly. The
        // unfiltered ("All") listing must return every agent in the
        // tenant regardless of active/deactivated state.
        User activeAgent = createAndSaveAgent(business, "still-active@test.com");
        User deactivatedAgent = createAndSaveAgent(business, "ravi-kumar@test.com");
        mockMvc.perform(post("/api/agents/" + deactivatedAgent.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/agents")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].id",
                        containsInAnyOrder(activeAgent.getId().intValue(), deactivatedAgent.getId().intValue())));

        // Also confirm the specific row still reports active:false so the
        // "All" tab's client-side filter and STATE column can display it
        // correctly.
        mockMvc.perform(get("/api/agents")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.data[?(@.id == " + deactivatedAgent.getId() + ")].active").value(false));
    }

    @Test
    void listAgents_withActiveFalseQueryParam_returnsOnlyDeactivatedAgents() throws Exception {
        // The active=false branch shares the same repository query shape
        // as active=true. Before this fix, that query additionally
        // required deletedAt IS NULL, which deactivate() always clears to
        // non-null — so active=false would have returned zero rows even
        // for a real deactivated agent. Not currently exercised by the
        // frontend, but must behave correctly as a documented, legitimate
        // filter value.
        createAndSaveAgent(business, "still-active-2@test.com");
        User deactivatedAgent = createAndSaveAgent(business, "deactivated-2@test.com");
        mockMvc.perform(post("/api/agents/" + deactivatedAgent.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/agents?active=false")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(deactivatedAgent.getId()))
                .andExpect(jsonPath("$.data[0].active").value(false));
    }

    @Test
    void listAgents_withNoQueryParam_neverLeaksOtherBusinessAgents() throws Exception {
        // Tenant scoping must survive the fix: relaxing the unfiltered
        // list to include deactivated agents must not also start
        // returning agents (active or deactivated) that belong to a
        // different business.
        Business otherBiz = createAndSaveBusiness("Other List Biz", "other-list@biz.com");
        User otherActiveAgent = createAndSaveAgent(otherBiz, "other-active@test.com");
        User otherDeactivatedAgent = createAndSaveAgent(otherBiz, "other-deactivated-list@test.com");
        User otherOwner = createAndSaveOwner(otherBiz, "other-owner-list@test.com");
        String otherOwnerToken = tokenFor(otherOwner);
        mockMvc.perform(post("/api/agents/" + otherDeactivatedAgent.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + otherOwnerToken))
                .andExpect(status().isNoContent());

        User ownAgent = createAndSaveAgent(business, "own-agent-list@test.com");

        mockMvc.perform(get("/api/agents")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(ownAgent.getId()));

        assertThat(otherActiveAgent.getId()).isNotNull();
    }

    @Test
    void getAgentById_returns200() throws Exception {
        User agent = createAndSaveAgent(business, "get-agent@test.com");

        mockMvc.perform(get("/api/agents/" + agent.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(agent.getId()))
                .andExpect(jsonPath("$.data.email").value("get-agent@test.com"));
    }

    @Test
    void getAgentById_usesFrontendContractFieldNames() throws Exception {
        // Matches exactly what OwnerAgentsPage.jsx reads off the detail
        // response: detail.name, detail.email, detail.phone,
        // detail.deliveredCount, detail.failedCount, detail.openCount,
        // detail.joinedAt, detail.active — never the backend's old
        // internal names (fullName/isActive/activeShipments/
        // totalDelivered/totalFailed/createdAt).
        User agent = createAndSaveAgent(business, "contract-detail-agent@test.com");

        mockMvc.perform(get("/api/agents/" + agent.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Test Agent"))
                .andExpect(jsonPath("$.data.email").value("contract-detail-agent@test.com"))
                .andExpect(jsonPath("$.data.phone").exists())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.openCount").value(0))
                .andExpect(jsonPath("$.data.deliveredCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.joinedAt").exists())
                .andExpect(jsonPath("$.data.fullName").doesNotExist())
                .andExpect(jsonPath("$.data.isActive").doesNotExist())
                .andExpect(jsonPath("$.data.activeShipments").doesNotExist())
                .andExpect(jsonPath("$.data.totalDelivered").doesNotExist())
                .andExpect(jsonPath("$.data.totalFailed").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").doesNotExist());
    }

    @Test
    void getAgent_fromDifferentBusiness_returns404() throws Exception {
        // Create a different business with its own agent
        Business otherBiz = createAndSaveBusiness("Other Biz", "other@biz.com");
        User otherAgent = createAndSaveAgent(otherBiz, "other-agent@test.com");

        // Try to access with the first business owner's token
        mockMvc.perform(get("/api/agents/" + otherAgent.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAgentById_afterDeactivate_returns200WithActiveFalse() throws Exception {
        // Bug found via manual E2E verification: POST /{id}/deactivate
        // succeeds (204), but the very next request the frontend makes -
        // GET /{id} to refresh the detail pane and show the "Reactivate"
        // button - used to 404 because AgentService.get() looked the agent
        // up via the active-only findAgent() (deletedAt IS NULL). The owner
        // must be able to view a deactivated agent's detail (active: false)
        // exactly as the "Active"/"All" list filter and the "Reactivate"
        // button imply is possible.
        User agent = createAndSaveAgent(business, "deactivated-detail-agent@test.com");

        mockMvc.perform(post("/api/agents/" + agent.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/agents/" + agent.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(agent.getId()))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    void getDeactivatedAgent_fromDifferentBusiness_stillReturns404() throws Exception {
        // Tenant scoping must survive the fix: a deactivated agent
        // belonging to ANOTHER business must still 404, not leak across
        // tenants just because the active-only filter was relaxed.
        Business otherBiz = createAndSaveBusiness("Other Deactivate Biz", "other-deactivate@biz.com");
        User otherAgent = createAndSaveAgent(otherBiz, "other-deactivated-agent@test.com");
        User otherOwner = createAndSaveOwner(otherBiz, "other-owner-deactivate@test.com");
        String otherOwnerToken = tokenFor(otherOwner);

        mockMvc.perform(post("/api/agents/" + otherAgent.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + otherOwnerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/agents/" + otherAgent.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAgent_returns200() throws Exception {
        User agent = createAndSaveAgent(business, "update-agent@test.com");

        String body = """
                {
                    "fullName": "Updated Name",
                    "phone": "+91-9999999999"
                }
                """;

        mockMvc.perform(put("/api/agents/" + agent.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.phone").value("+91-9999999999"));
    }

    @Test
    void updateAgent_withNameFieldInsteadOfFullName_returns200() throws Exception {
        // Matches exactly what the real frontend sends: agentsApi.js's
        // updateAgent(id, {name, phone}) posts a "name" key, never
        // "fullName". UpdateAgentRequest.fullName must accept it via
        // @JsonAlias, the same pattern already used for note/notes.
        User agent = createAndSaveAgent(business, "update-name-alias-agent@test.com");

        String body = """
                {
                    "name": "Updated Via Name Key",
                    "phone": "+91-9888888888"
                }
                """;

        mockMvc.perform(put("/api/agents/" + agent.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Via Name Key"))
                .andExpect(jsonPath("$.data.phone").value("+91-9888888888"));
    }

    @Test
    void deactivateAgent_noActiveShipments_returns204() throws Exception {
        User agent = createAndSaveAgent(business, "del-agent@test.com");

        mockMvc.perform(post("/api/agents/" + agent.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteAgent_withActiveShipments_returns422() throws Exception {
        User agent = createAndSaveAgent(business, "busy-agent@test.com");

        // Create an active shipment assigned to this agent
        Shipment shipment = Shipment.builder()
                .trackingToken("track-" + System.nanoTime())
                .business(business)
                .assignedAgent(agent)
                .status(ShipmentStatus.ASSIGNED)
                .customerName("Customer")
                .customerPhone("+91-9000000000")
                .deliveryAddress("123 Test St")
                .build();
        shipmentRepository.save(shipment);

        mockMvc.perform(post("/api/agents/" + agent.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deactivateAgentViaPostPath() throws Exception {
        User agent = createAndSaveAgent(business, "post-deactivate@test.com");

        mockMvc.perform(post("/api/agents/" + agent.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(agent.getId()).orElseThrow().getIsActive()).isFalse();
    }

    @Test
    void reactivateAgentRestoresActiveFlag() throws Exception {
        User agent = createAndSaveAgent(business, "post-reactivate@test.com");
        mockMvc.perform(post("/api/agents/" + agent.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/agents/" + agent.getId() + "/reactivate")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        User reloaded = userRepository.findById(agent.getId()).orElseThrow();
        assertThat(reloaded.getIsActive()).isTrue();
        assertThat(reloaded.getDeletedAt()).isNull();
    }

    @Test
    void reactivatedAgentIsEligibleForAutoAssignmentAgain() throws Exception {
        User agent = createAndSaveAgent(business, "reactivate-assign@test.com");

        mockMvc.perform(post("/api/agents/" + agent.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/agents/" + agent.getId() + "/reactivate")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        String createBody = """
                {
                    "customerName": "Reactivation Customer",
                    "customerPhone": "+91-9000000003",
                    "deliveryAddress": "789 Reactivate St"
                }
                """;

        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.agentId").value(agent.getId()));
    }
}
