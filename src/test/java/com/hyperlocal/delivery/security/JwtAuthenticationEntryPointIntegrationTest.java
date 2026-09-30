package com.hyperlocal.delivery.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.hyperlocal.delivery.BaseIntegrationTest;

/**
 * The 401 envelope written by {@link JwtAuthenticationEntryPoint} must be
 * declared as UTF-8 JSON, both when no token is sent (Spring Security calls
 * the entry point) and when a bad token is sent ({@link JwtAuthFilter}
 * calls {@code write} directly).
 */
class JwtAuthenticationEntryPointIntegrationTest extends BaseIntegrationTest {

    private static final String UTF8_JSON = "application/json;charset=UTF-8";

    @Test
    void noToken_returns401_asUtf8Json() throws Exception {
        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(UTF8_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidToken_returns401_asUtf8Json() throws Exception {
        mockMvc.perform(get("/api/agents").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(UTF8_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
