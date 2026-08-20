package com.hyperlocal.delivery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

import com.hyperlocal.delivery.service.AgentInviteMailer;
import com.hyperlocal.delivery.service.ConsoleAgentInviteMailer;
import com.hyperlocal.delivery.service.ConsoleMailService;
import com.hyperlocal.delivery.service.ConsoleOtpEmailService;
import com.hyperlocal.delivery.service.OtpEmailService;
import com.hyperlocal.delivery.service.PasswordResetMailer;
import com.hyperlocal.delivery.service.SmtpAgentInviteMailer;
import com.hyperlocal.delivery.service.SmtpMailService;
import com.hyperlocal.delivery.service.SmtpOtpEmailService;

/**
 * Binds the {@code app.mail.*} configuration block and wires up exactly one
 * {@link PasswordResetMailer} bean, regardless of profile:
 *
 * <ul>
 *   <li>{@link SmtpMailService} registers only when {@code spring.mail.host}
 *       actually resolves to a non-blank value (true in prod when SMTP_HOST
 *       is set).</li>
 *   <li>{@link ConsoleMailService} is the fallback whenever no other
 *       {@code PasswordResetMailer} bean exists — dev, test, and prod with
 *       SMTP unconfigured (so a prod deploy without SMTP_HOST degrades to
 *       console-logging the reset link instead of failing to start).</li>
 * </ul>
 *
 * <p>Also wires up exactly one {@link OtpEmailService} bean, similarly —
 * but its console fallback additionally requires {@code dev}/{@code test}/
 * {@code mysql-test}, unlike the two pairs above. It used to be two
 * independently-conditioned {@code @Component}s — {@code SmtpOtpEmailService}
 * on {@code @ConditionalOnExpression(mail.host non-blank)},
 * {@code ConsoleOtpEmailService} on {@code @Profile({"dev","test",
 * "mysql-test"})} alone — which could both be true at once (the {@code dev}
 * profile with real SMTP configured for local OTP testing) and crashed
 * startup with "required a single bean, but 2 were found" instead of
 * picking one. Stacking {@code @ConditionalOnMissingBean} onto the existing
 * {@code @Profile} fixes that without also silently letting a prod deploy
 * with SMTP misconfigured start up logging live OTP codes in plaintext
 * instead of failing loudly — see that bean method's own Javadoc.
 *
 * <p>All mailers are registered here as {@code @Bean} methods (rather than
 * component-scanned {@code @Component}s) so the {@code @ConditionalOnMissingBean}
 * fallback is order-safe: within a single {@code @Configuration} class,
 * {@code @Bean} methods are processed in declaration order, so
 * {@code consoleMailService()} reliably sees whether {@code smtpMailService()}
 * already registered.
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    /**
     * The placeholder is resolved against the environment first and then
     * evaluated as a SpEL boolean expression, so a blank/unset
     * {@code spring.mail.host} (the {@code ${SMTP_HOST:}} empty default)
     * deterministically evaluates to {@code false} here — unlike a plain
     * {@code @ConditionalOnProperty}, which treats an empty-but-present
     * property value as a match.
     */
    @Bean
    @ConditionalOnExpression("!'${spring.mail.host:}'.isBlank()")
    public PasswordResetMailer smtpMailService(JavaMailSender mailSender, MailProperties mailProperties) {
        return new SmtpMailService(mailSender, mailProperties);
    }

    @Bean
    @ConditionalOnMissingBean(PasswordResetMailer.class)
    public PasswordResetMailer consoleMailService(MailProperties mailProperties) {
        return new ConsoleMailService(mailProperties);
    }

    /** Agent invites, selected on exactly the same condition. */
    @Bean
    @ConditionalOnExpression("!'${spring.mail.host:}'.isBlank()")
    public AgentInviteMailer smtpAgentInviteMailer(JavaMailSender mailSender, MailProperties mailProperties) {
        return new SmtpAgentInviteMailer(mailSender, mailProperties);
    }

    @Bean
    @ConditionalOnMissingBean(AgentInviteMailer.class)
    public AgentInviteMailer consoleAgentInviteMailer(MailProperties mailProperties) {
        return new ConsoleAgentInviteMailer(mailProperties);
    }

    /**
     * OTP verification emails. Unlike the two pairs above, the console
     * fallback here stays restricted to {@code dev}/{@code test}/
     * {@code mysql-test} via {@code @Profile}, on top of the same
     * {@code @ConditionalOnMissingBean}: an OTP code is a live credential
     * (registration/login), not a one-time reset link, so a misconfigured
     * prod deploy (SMTP_HOST blank) should still fail loudly at startup
     * instead of silently degrading to logging real users' OTP codes in
     * plaintext. The {@code @ConditionalOnMissingBean} half is what closes
     * the original bug: within dev/test with SMTP *also* configured (e.g.
     * for local OTP-email testing), it stops this bean from registering
     * alongside {@code smtpOtpEmailService} and crashing startup with
     * "required a single bean, but 2 were found".
     */
    @Bean
    @ConditionalOnExpression("!'${spring.mail.host:}'.isBlank()")
    public OtpEmailService smtpOtpEmailService(
            JavaMailSender mailSender, @Value("${spring.mail.username:}") String fromAddress) {
        return new SmtpOtpEmailService(mailSender, fromAddress);
    }

    @Bean
    @Profile({"dev", "test", "mysql-test"})
    @ConditionalOnMissingBean(OtpEmailService.class)
    public OtpEmailService consoleOtpEmailService() {
        return new ConsoleOtpEmailService();
    }
}
