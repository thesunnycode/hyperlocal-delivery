package com.hyperlocal.delivery.config;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.hyperlocal.delivery.BaseIntegrationTest;

/**
 * The document-level tag list in {@link OpenApiConfig} must cover every
 * {@code @Tag} a controller uses, each with a description. "Reports" had no
 * description anywhere and was absent from the generated list. The order
 * springdoc emits is not significant, so it is not asserted.
 */
class OpenApiConfigIntegrationTest extends BaseIntegrationTest {

    @Test
    void apiDocs_listEveryControllerTag_withDescriptions() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[*].name", containsInAnyOrder(
                        "Auth", "Agents", "Shipments", "Delivery Attempts",
                        "Public Tracking", "Analytics", "Reports", "Health")))
                .andExpect(jsonPath("$.tags[?(@.name == 'Reports')].description").isNotEmpty())
                .andExpect(jsonPath("$.tags[?(@.name == 'Health')].description").isNotEmpty());
    }
}
