package com.hyperlocal.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Strongly-typed binding of the {@code app.mail.*} configuration block.
 *
 * @param resetLinkBaseUrl     base URL of the frontend's reset-password page;
 *                             the reset token is appended as
 *                             {@code ?token=...} (or {@code &token=...} if
 *                             the base URL already has a query string)
 * @param resetTokenTtlMinutes how long a password-reset token remains valid
 *                             after issuance
 * @param inviteLinkBaseUrl    base URL of the frontend's agent-setup page; the
 *                             invite token is appended the same way
 * @param inviteTtlHours       how long an agent invite remains valid. Measured
 *                             in hours, not minutes: an owner sends this to
 *                             someone who may not read it until their next
 *                             shift, and a link that dies in half an hour is a
 *                             link that always needs re-sending
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        String resetLinkBaseUrl,
        @DefaultValue("30") int resetTokenTtlMinutes,
        @DefaultValue("http://localhost:5173/agent-setup") String inviteLinkBaseUrl,
        @DefaultValue("168") int inviteTtlHours) {
}
