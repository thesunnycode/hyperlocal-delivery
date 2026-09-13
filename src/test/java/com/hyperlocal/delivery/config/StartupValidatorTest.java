package com.hyperlocal.delivery.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit tests for {@link StartupValidator}'s prod-only fail-fast checks.
 *
 * <p>An unset {@code ${VAR}} in a bound property is not an error to Spring:
 * the literal text is bound instead. These tests pin that the validator
 * catches that (and blank values) in prod, and ignores every other profile.
 */
class StartupValidatorTest {

    private static final String GOOD_CORS = "https://app.example.com";
    private static final String GOOD_INVITE = "https://app.example.com/agent-setup";

    private static StartupValidator validator(String profile, String cors, String inviteUrl) {
        MockEnvironment env = new MockEnvironment();
        if (profile != null) {
            env.setActiveProfiles(profile);
        }
        return new StartupValidator(
                new CorsConfigProperties(cors), new MailProperties(inviteUrl, 168), env);
    }

    @Test
    void prod_withRealValues_passes() {
        assertThatCode(() -> validator("prod", GOOD_CORS, GOOD_INVITE).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void prod_unresolvedCorsOriginsPlaceholder_failsNamingTheVariable() {
        assertThatThrownBy(() -> validator("prod", "${CORS_ORIGINS}", GOOD_INVITE).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ORIGINS")
                .hasMessageContaining("app.cors.allowed-origins");
    }

    @Test
    void prod_blankCorsOrigins_fails() {
        assertThatThrownBy(() -> validator("prod", "  ", GOOD_INVITE).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ORIGINS");
    }

    @Test
    void prod_nullCorsOrigins_fails() {
        assertThatThrownBy(() -> validator("prod", null, GOOD_INVITE).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ORIGINS");
    }

    @Test
    void prod_unresolvedInviteLinkBaseUrlPlaceholder_failsNamingTheVariable() {
        assertThatThrownBy(() -> validator("prod", GOOD_CORS, "${INVITE_LINK_BASE_URL}").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INVITE_LINK_BASE_URL")
                .hasMessageContaining("app.mail.invite-link-base-url");
    }

    @Test
    void prod_blankInviteLinkBaseUrl_fails() {
        assertThatThrownBy(() -> validator("prod", GOOD_CORS, "").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INVITE_LINK_BASE_URL");
    }

    @Test
    void prod_bothUnresolved_reportsBothInOneMessage() {
        assertThatThrownBy(() -> validator("prod", "${CORS_ORIGINS}", "${INVITE_LINK_BASE_URL}")
                        .afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ORIGINS")
                .hasMessageContaining("INVITE_LINK_BASE_URL");
    }

    @Test
    void nonProdProfiles_areNotValidated() {
        for (String profile : new String[] { null, "dev", "test", "mysql-test" }) {
            assertThatCode(() -> validator(profile, "${CORS_ORIGINS}", "${INVITE_LINK_BASE_URL}")
                            .afterPropertiesSet())
                    .as("profile %s", profile)
                    .doesNotThrowAnyException();
        }
    }
}
