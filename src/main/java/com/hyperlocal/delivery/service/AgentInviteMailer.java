package com.hyperlocal.delivery.service;

/**
 * Sends the agent invite email. Two implementations, chosen the same way as
 * {@link PasswordResetMailer}: {@link ConsoleAgentInviteMailer} logs the link
 * locally, {@link SmtpAgentInviteMailer} sends it for real.
 */
public interface AgentInviteMailer {

    /**
     * Send (or log) an invite link.
     *
     * @param toEmail      the agent's address, which is also their login
     * @param agentName    used to address the message
     * @param businessName the business that created the account
     * @param rawToken     the plaintext token — never persisted, only its hash
     * @return {@code true} if a real email was sent. {@code false} means the
     *         link was only logged, and the owner has to deliver it. The
     *         caller passes this on so no screen claims an email was sent
     *         when none was.
     */
    boolean sendInviteEmail(String toEmail, String agentName, String businessName, String rawToken);
}
