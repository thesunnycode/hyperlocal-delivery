package com.hyperlocal.delivery.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.hyperlocal.delivery.config.MailProperties;

import lombok.RequiredArgsConstructor;

/**
 * Real invite sender. Registered only when {@code spring.mail.host} is set.
 *
 * <p>A failure here is logged and reported as "not emailed" rather than
 * thrown: the invite row is already committed and the owner console shows the
 * link, so the owner can still deliver it by hand. Failing the whole request
 * would leave a usable invite the owner never saw.
 */
@RequiredArgsConstructor
public class SmtpAgentInviteMailer implements AgentInviteMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpAgentInviteMailer.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    @Override
    public boolean sendInviteEmail(String toEmail, String agentName, String businessName, String rawToken) {
        String link = MailLinkBuilder.buildInviteLink(mailProperties.inviteLinkBaseUrl(), rawToken);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Set up your " + businessName + " delivery account");
        message.setText("""
                Hi %s,

                %s has added you as a delivery agent. Set your password here:

                %s

                Sign in with this email address: %s

                The link works once and expires. If it has already expired, ask
                %s to send you a new one.
                """.formatted(agentName, businessName, link, toEmail, businessName));
        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            log.error("Failed to send invite email to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }
}
