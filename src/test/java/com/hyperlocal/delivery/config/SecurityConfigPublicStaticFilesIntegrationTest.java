package com.hyperlocal.delivery.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.hyperlocal.delivery.BaseIntegrationTest;

/**
 * The SPA shell and the root files the frontend ships must stay reachable
 * without a token. The assertion is on the security layer only (never 401
 * or 403), not on 200: {@code src/main/resources/static} is build output,
 * so a checkout without a built frontend returns 404 for these paths, and
 * that is still "public".
 */
class SecurityConfigPublicStaticFilesIntegrationTest extends BaseIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/",
            "/index.html",
            "/favicon.svg",
            "/robots.txt",
            "/delivery-rider-loop.webm",
            "/assets/app-abc123.js",
            "/agent/shipments/2"
    })
    void shippedStaticFilesAndSpaRoutes_arePublic(String path) throws Exception {
        int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
        assertThat(status).as("GET %s without a token", path).isNotIn(401, 403);
    }

    /** Control: proves the check above would see a 401 if a path were secured. */
    @Test
    void apiPath_withoutToken_is401() throws Exception {
        int status = mockMvc.perform(get("/api/agents")).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(401);
    }
}
