package com.hyperlocal.delivery.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import com.hyperlocal.delivery.BaseIntegrationTest;

class SwaggerAndSpaRoutingIntegrationTest extends BaseIntegrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui/index.html", "/swagger-ui/swagger-ui.css", "/swagger-ui/swagger-ui-bundle.js"})
    void swaggerIsServedAsAResource(String path) throws Exception {
        var result = mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        assertThat(result.getHandler()).isInstanceOf(ResourceHttpRequestHandler.class);
        assertThat(result.getResponse().getForwardedUrl()).isNull();
        assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/owner/shipments", "/agent/shipments/2", "/login"})
    void clientRoutesStillForwardToTheSpa(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(forwardedUrl("/index.html"));
    }
}
