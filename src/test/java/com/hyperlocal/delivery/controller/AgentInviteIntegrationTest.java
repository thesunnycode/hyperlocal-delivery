package com.hyperlocal.delivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hyperlocal.delivery.BaseIntegrationTest;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.repository.AgentInviteRepository;
import com.jayway.jsonpath.JsonPath;

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

    @Test
    void issueInvite_expiresAt_isWholeSeconds_andMatchesPreview() throws Exception {
        User agent = createAndSaveAgent(business, "invite-seconds@test.com");

        String body = mockMvc.perform(post("/api/agents/" + agent.getId() + "/invite")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String issuedExpiresAt = JsonPath.read(body, "$.data.expiresAt");
        String inviteUrl = JsonPath.read(body, "$.data.inviteUrl");
        String token = inviteUrl.substring(inviteUrl.indexOf("token=") + "token=".length());

        assertThat(Instant.parse(issuedExpiresAt).getNano())
                .as("issue() must not report sub-second digits the DATETIME column drops")
                .isZero();

        flushAndClear();
        String previewBody = mockMvc.perform(get("/api/auth/invite/" + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String previewExpiresAt = JsonPath.read(previewBody, "$.data.expiresAt");

        assertThat(previewExpiresAt).isEqualTo(issuedExpiresAt);
    }
}
