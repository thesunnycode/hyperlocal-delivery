package com.hyperlocal.delivery.model;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UserRoleSerializationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void businessOwnerSerializesToShortCode() throws Exception {
        assertThat(mapper.writeValueAsString(UserRole.BUSINESS_OWNER)).isEqualTo("\"OWNER\"");
    }

    @Test
    void deliveryAgentSerializesToShortCode() throws Exception {
        assertThat(mapper.writeValueAsString(UserRole.DELIVERY_AGENT)).isEqualTo("\"AGENT\"");
    }

    @Test
    void enumNameIsUnchangedForAuthorityBuilding() {
        assertThat(UserRole.BUSINESS_OWNER.name()).isEqualTo("BUSINESS_OWNER");
        assertThat(UserRole.DELIVERY_AGENT.name()).isEqualTo("DELIVERY_AGENT");
    }
}
