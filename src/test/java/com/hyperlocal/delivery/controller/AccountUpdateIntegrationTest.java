package com.hyperlocal.delivery.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.hyperlocal.delivery.BaseIntegrationTest;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.User;

/**
 * Integration tests for the owner-editable-account endpoint (X3).
 */
class AccountUpdateIntegrationTest extends BaseIntegrationTest {

    private Business business;
    private User owner;
    private User agent;
    private String ownerToken;
    private String agentToken;

    @BeforeEach
    void setUp() {
        business = createAndSaveBusiness("Account Biz", "accountbiz@test.com");
        owner = createAndSaveOwner(business, "accountowner@test.com");
        agent = createAndSaveAgent(business, "accountagent@test.com");
        ownerToken = tokenFor(owner);
        agentToken = tokenFor(agent);
    }

    @Test
    void updateMe_changesFullNameAndPhone() throws Exception {
        mockMvc.perform(patch("/api/auth/me")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Updated Owner Name", "phone": "+91-9999999999"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Owner Name"))
                .andExpect(jsonPath("$.data.phone").value("+91-9999999999"))
                .andExpect(jsonPath("$.data.email").value(owner.getEmail()));
    }

    @Test
    void updateMe_partialUpdate_leavesOtherFieldUnchanged() throws Exception {
        mockMvc.perform(patch("/api/auth/me")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Only Name Changed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Only Name Changed"))
                .andExpect(jsonPath("$.data.phone").value(owner.getPhone()));
    }

    @Test
    void updateMe_emailAndRoleFieldsAreNotAccepted_remainUnchanged() throws Exception {
        // email/role aren't fields on UpdateAccountRequest at all — sending
        // them is simply ignored by Jackson, proving they can't be changed
        // through this endpoint.
        mockMvc.perform(patch("/api/auth/me")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "hacker@test.com", "role": "DELIVERY_AGENT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(owner.getEmail()))
                .andExpect(jsonPath("$.data.role").value("OWNER"));
    }

    /**
     * Finding #8 (X3): an owner can rename their business through this
     * endpoint — the design mock's "BUSINESS NAME · OWNER ONLY" field.
     */
    @Test
    void updateMe_ownerChangesBusinessName_reflectedInResponse() throws Exception {
        mockMvc.perform(patch("/api/auth/me")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessName": "Renamed Delivery Co"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessName").value("Renamed Delivery Co"));
    }

    /**
     * A delivery agent has no business to rename: sending businessName is
     * silently ignored (same posture as email/role), while their own
     * fullName update still goes through.
     */
    @Test
    void updateMe_agentSendingBusinessName_isIgnored_fullNameStillUpdates() throws Exception {
        mockMvc.perform(patch("/api/auth/me")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Updated Agent Name", "businessName": "Hijacked Name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Agent Name"))
                .andExpect(jsonPath("$.data.businessName").value(business.getName()));
    }

    @Test
    void updateMe_ownerChangesBusinessPhone_reflectedInResponse() throws Exception {
        mockMvc.perform(patch("/api/auth/me")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessPhone": "+91-9222222222"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessPhone").value("+91-9222222222"));
    }

    @Test
    void updateMe_agentSendingBusinessPhone_isIgnored() throws Exception {
        Business business = createAndSaveBusiness("Phone Agent Biz", "phoneagentbiz@test.com");
        User agent = createAndSaveAgent(business, "phoneagent@test.com");
        String agentToken = tokenFor(agent);

        mockMvc.perform(patch("/api/auth/me")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Agent Kept Name", "businessPhone": "+91-9333333333"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Agent Kept Name"))
                .andExpect(jsonPath("$.data.businessPhone").doesNotExist());
    }
}
