package com.hyperlocal.delivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.HashSet;
import java.util.Set;

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
 * Integration test for public tracking endpoint (task 18.4).
 * Verifies that the public tracking response contains only safe fields
 * and excludes sensitive information.
 */
class PublicTrackingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private Business business;
    private User owner;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        business = createAndSaveBusiness("Track Biz", "trackbiz@test.com");
        owner = createAndSaveOwner(business, "trackowner@test.com");
        createAndSaveAgent(business, "trackagent@test.com");
        ownerToken = tokenFor(owner);
    }

    @Test
    void publicTracking_containsExpectedFields_excludesSensitiveData() throws Exception {
        // Create a shipment to get a tracking token
        String createBody = """
                {
                    "customerName": "Public Customer",
                    "customerPhone": "+91-9000000099",
                    "deliveryAddress": "100 Public Ave"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String trackingToken = createResponse.get("data").get("token").asString();

        // Call public tracking endpoint WITHOUT auth
        mockMvc.perform(get("/api/track/" + trackingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                // Expected fields present
                .andExpect(jsonPath("$.data.trackingToken").value(trackingToken))
                .andExpect(jsonPath("$.data.status").exists())
                .andExpect(jsonPath("$.data.customerName").value("Public Customer"))
                .andExpect(jsonPath("$.data.address").value("100 Public Ave"))
                .andExpect(jsonPath("$.data.events").isArray())
                // Sensitive fields ABSENT
                .andExpect(jsonPath("$.data.customerPhone").doesNotExist())
                .andExpect(jsonPath("$.data.agentId").doesNotExist())
                .andExpect(jsonPath("$.data.agentName").doesNotExist())
                .andExpect(jsonPath("$.data.agentPhone").doesNotExist())
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.deliveryAttempts").doesNotExist());
    }

    /**
     * Task 18 regression: an exhaustive allowlist of every field
     * {@link com.hyperlocal.delivery.dto.tracking.PublicTrackingResponse} is
     * permitted to expose. Unlike the individual {@code doesNotExist()}
     * checks above (which only catch fields we thought to name), this fails
     * the instant a new field is added to the record without a corresponding
     * update here — closing the gap those checks can't.
     */
    @Test
    void publicTracking_responseHasExactlyAllowedFields_noUnexpectedLeaks() throws Exception {
        String createBody = """
                {
                    "customerName": "Allowlist Customer",
                    "customerPhone": "+91-9000000095",
                    "deliveryAddress": "5 Allowlist Ave"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String trackingToken = createResponse.get("data").get("token").asString();

        MvcResult trackResult = mockMvc.perform(get("/api/track/" + trackingToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(trackResult.getResponse().getContentAsString()).get("data");

        Set<String> allowedFields = Set.of(
                "trackingToken", "status", "customerName", "address",
                "scheduledAt", "deliveredAt", "businessName", "businessPhone", "events");

        Set<String> actualFields = new HashSet<>(data.propertyNames());

        assertThat(actualFields).isEqualTo(allowedFields);
    }

    /**
     * Bug found via manual E2E verification (same bug class as
     * CreateShipmentRequest/ShipmentResponseDto): CustomerTrackingPage.jsx
     * reads {@code s.address}, {@code s.scheduledAt}, and {@code s.events}
     * (each item read as {@code {status, label, stamp}}) — not {@code
     * deliveryAddress}, {@code scheduledDeliveryAt}, or a {@code timeline}
     * array of {@code {status, at}}. Without this fix, the public tracking
     * page customers reach via SMS renders the address, scheduled time, and
     * entire status history as blank.
     */
    @Test
    void publicTracking_returnsFrontendContractShape_addressScheduledAtAndEvents() throws Exception {
        String createBody = """
                {
                    "customerName": "Frontend Shape Customer",
                    "customerPhone": "+91-9000000094",
                    "deliveryAddress": "94 Frontend Shape Ave",
                    "scheduledDeliveryAt": "2030-01-01T10:00:00"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String trackingToken = createResponse.get("data").get("token").asString();

        flushAndClear();

        mockMvc.perform(get("/api/track/" + trackingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").value("94 Frontend Shape Ave"))
                .andExpect(jsonPath("$.data.scheduledAt").exists())
                .andExpect(jsonPath("$.data.deliveryAddress").doesNotExist())
                .andExpect(jsonPath("$.data.scheduledDeliveryAt").doesNotExist())
                .andExpect(jsonPath("$.data.timeline").doesNotExist())
                .andExpect(jsonPath("$.data.events").isArray())
                .andExpect(jsonPath("$.data.events[0].status").exists())
                .andExpect(jsonPath("$.data.events[0].label").value("Assigned"))
                .andExpect(jsonPath("$.data.events[0].stamp").exists())
                .andExpect(jsonPath("$.data.events[0].at").doesNotExist())
                // Still privacy-safe: no agent identity, phone, ids, or attempt data.
                .andExpect(jsonPath("$.data.customerPhone").doesNotExist())
                .andExpect(jsonPath("$.data.agentId").doesNotExist())
                .andExpect(jsonPath("$.data.agentName").doesNotExist())
                .andExpect(jsonPath("$.data.agentPhone").doesNotExist())
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.events[0].changedBy").doesNotExist())
                .andExpect(jsonPath("$.data.events[0].notes").doesNotExist());
    }

    @Test
    void publicTracking_invalidToken_returns404() throws Exception {
        mockMvc.perform(get("/api/track/nonexistent-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicTracking_businessWithNoPhone_omitsBusinessPhone() throws Exception {
        String createBody = """
                {
                    "customerName": "No Phone Customer",
                    "customerPhone": "+91-9000000098",
                    "deliveryAddress": "1 No Phone Ave"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String trackingToken = createResponse.get("data").get("token").asString();

        mockMvc.perform(get("/api/track/" + trackingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessPhone").doesNotExist());
    }

    @Test
    void publicTrackingIsReachableAtApiTrackPath() throws Exception {
        String createBody = """
                {
                    "customerName": "Api Track Customer",
                    "customerPhone": "+91-9000000096",
                    "deliveryAddress": "3 Api Track Ave"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String trackingToken = createResponse.get("data").get("token").asString();

        mockMvc.perform(get("/api/track/" + trackingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackingToken").value(trackingToken))
                .andExpect(jsonPath("$.data.customerPhone").doesNotExist())
                .andExpect(jsonPath("$.data.agentId").doesNotExist())
                .andExpect(jsonPath("$.data.agentName").doesNotExist())
                .andExpect(jsonPath("$.data.agentPhone").doesNotExist());
    }

    @Test
    void oldPublicPrefixNoLongerReachable() throws Exception {
        // The route has moved from /api/public/track/{token} to /api/track/{token}.
        // This confirms the old prefix is gone rather than duplicated, and that
        // no other production code path still depends on /api/public/**.
        mockMvc.perform(get("/api/public/track/anything"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void publicTracking_businessWithPhone_includesBusinessPhone() throws Exception {
        businessRepository.findById(business.getId()).ifPresent(b -> {
            b.setPhone("+91-9111111111");
            businessRepository.save(b);
        });

        String createBody = """
                {
                    "customerName": "Phone Customer",
                    "customerPhone": "+91-9000000097",
                    "deliveryAddress": "2 Phone Ave"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String trackingToken = createResponse.get("data").get("token").asString();

        flushAndClear();
        mockMvc.perform(get("/api/track/" + trackingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessPhone").value("+91-9111111111"));
    }
}
