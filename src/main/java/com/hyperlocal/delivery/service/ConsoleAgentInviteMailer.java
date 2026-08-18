package com.hyperlocal.delivery.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hyperlocal.delivery.config.MailProperties;

import lombok.RequiredArgsConstructor;

/**
 * Fallback invite mailer: logs the link instead of sending it. Used locally,
 * in test, and in production when SMTP is not configured.
 *
 * <p>Returns {@code false} so callers can tell the owner the truth — that
 * nothing was sent and the link is theirs to deliver.
 */
@RequiredArgsConstructor
public class ConsoleAgentInviteMailer implements AgentInviteMailer {

    private static final Logger log = LoggerFactory.getLogger(ConsoleAgentInviteMailer.class);

    private final MailProperties mailProperties;

    @Override
    public boolean sendInviteEmail(String toEmail, String agentName, String businessName, String rawToken) {
        String link = MailLinkBuilder.buildInviteLink(mailProperties.inviteLinkBaseUrl(), rawToken);
        log.info("[DEV MAIL] Invite for {} ({} at {}). Link: {}", toEmail, agentName, businessName, link);
        return false;
    }
}
