package com.hyperlocal.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hyperlocal.delivery.BaseIntegrationTest;
import com.hyperlocal.delivery.model.AgentInvite;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.repository.AgentInviteRepository;

/**
 * Integration tests for the housekeeping in {@link ScheduledCleanupJob}.
 * The job methods are invoked directly rather than waiting on the cron.
 */
class ScheduledCleanupJobIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ScheduledCleanupJob cleanupJob;

    @Autowired
    private AgentInviteRepository inviteRepository;

    @Test
    void purgeExpiredTokens_alsoDeletesExpiredAgentInvites_andKeepsLiveOnes() {
        Business business = createAndSaveBusiness("Cleanup Biz", "cleanup-biz@test.com");
        User agent = createAndSaveAgent(business, "cleanup-agent@test.com");
        LocalDateTime now = LocalDateTime.now();

        AgentInvite expired = inviteRepository.save(AgentInvite.builder()
                .user(agent)
                .tokenHash("a".repeat(64))
                .expiresAt(now.minusHours(1))
                .build());
        AgentInvite live = inviteRepository.save(AgentInvite.builder()
                .user(agent)
                .tokenHash("b".repeat(64))
                .expiresAt(now.plusHours(1))
                .build());
        flushAndClear();

        cleanupJob.purgeExpiredTokens();
        flushAndClear();

        assertThat(inviteRepository.findById(expired.getId())).isEmpty();
        assertThat(inviteRepository.findById(live.getId())).isPresent();
    }
}
