package com.hyperlocal.delivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hyperlocal.delivery.BaseIntegrationTest;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.repository.AgentInviteRepository;

/**
 * Integration tests for {@code POST /api/agents/{id}/invite} and the public
 * invite preview it feeds.
 */
class AgentInviteIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AgentInviteRepository inviteRepository;

    private Business business;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        business = createAndSaveBusiness("Invite Biz", "invite-biz@test.com");
        User owner = createAndSaveOwner(business, "invite-owner@test.com");
        ownerToken = tokenFor(owner);
    }

    @Test
    void issueInvite_forActiveAgent_returns200() throws Exception {
        User agent = createAndSaveAgent(business, "invite-active@test.com");

        mockMvc.perform(post("/api/agents/" + agent.getId() + "/invite")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteUrl").isString())
                .andExpect(jsonPath("$.data.expiresAt").isString());
    }

    @Test
    void issueInvite_forDeactivatedAgent_returns422InvalidAgent() throws Exception {
        User agent = createAndSaveAgent(business, "invite-deactivated@test.com");
        mockMvc.perform(post("/api/agents/" + agent.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().is2xxSuccessful());
        long invitesBefore = inviteRepository.count();

        mockMvc.perform(post("/api/agents/" + agent.getId() + "/invite")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_AGENT"));

        // No password-setting link may exist for an account that cannot sign in.
        assertThat(inviteRepository.count()).isEqualTo(invitesBefore);
    }
}
