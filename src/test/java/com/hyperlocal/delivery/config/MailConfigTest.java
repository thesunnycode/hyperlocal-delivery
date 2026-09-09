package com.hyperlocal.delivery.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.hyperlocal.delivery.service.ConsoleMailService;
import com.hyperlocal.delivery.service.PasswordResetMailer;
import com.hyperlocal.delivery.service.SmtpMailService;

/**
 * Verifies {@link MailConfig} registers exactly one {@link PasswordResetMailer}
 * bean in every deploy scenario from finding #1 of the pre-merge review — the
 * whole point of the fix is that a prod deploy without SMTP configured must
 * degrade to console-logging the reset link instead of failing to start.
 *
 * <p>Uses {@link ApplicationContextRunner} (no full {@code @SpringBootTest})
 * so these run without a database, exercising exactly the bean-registration
 * logic in {@link MailConfig} against the real {@link MailSenderAutoConfiguration}.
 */
class MailConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(MailConfig.class)
            .withPropertyValues("app.mail.reset-link-base-url=http://localhost:5173/reset-password");

    @Test
    void prodWithSmtpConfigured_registersOnlySmtpMailService() {
        contextRunner
                .withPropertyValues("spring.mail.host=smtp.example.com")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(PasswordResetMailer.class);
                    assertThat(ctx.getBean(PasswordResetMailer.class)).isInstanceOf(SmtpMailService.class);
                });
    }

    @Test
    void prodWithSmtpHostBlank_degradesToConsoleMailService() {
        // Mirrors application-prod.yml's ${SMTP_HOST:} empty default when
        // the SMTP_HOST env var is unset: the property key IS present, but
        // with an empty value. This is exactly the case a naive
        // @ConditionalOnProperty would get wrong (empty-but-present matches
        // by default), which is why MailConfig uses @ConditionalOnExpression.
        contextRunner
                .withPropertyValues("spring.mail.host=")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(PasswordResetMailer.class);
                    assertThat(ctx.getBean(PasswordResetMailer.class)).isInstanceOf(ConsoleMailService.class);
                });
    }

    @Test
    void nonProdWithNoMailPropertiesAtAll_usesConsoleMailService() {
        // Mirrors application.yml (dev/default profile): spring.mail.* is
        // never set at all, unlike the prod profile's blank default.
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(PasswordResetMailer.class);
            assertThat(ctx.getBean(PasswordResetMailer.class)).isInstanceOf(ConsoleMailService.class);
        });
    }

    @Test
    void testProfileDefaults_useConsoleMailService() {
        // Mirrors application-test.yml / application-mysql-test.yml: same
        // shape as the non-prod case above (no spring.mail.* configured),
        // asserted separately because it's the profile the rest of the
        // integration suite actually boots under.
        contextRunner
                .withPropertyValues("app.mail.reset-token-ttl-minutes=30")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(PasswordResetMailer.class);
                    assertThat(ctx.getBean(PasswordResetMailer.class)).isInstanceOf(ConsoleMailService.class);
                });
    }
}
