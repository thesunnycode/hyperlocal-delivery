package com.hyperlocal.delivery.model;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FailureReasonSerializationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesFrontendLabels() throws Exception {
        assertThat(mapper.readValue("\"Customer absent\"", FailureReason.class)).isEqualTo(FailureReason.CUSTOMER_ABSENT);
        assertThat(mapper.readValue("\"Address not found\"", FailureReason.class)).isEqualTo(FailureReason.ADDRESS_NOT_FOUND);
        assertThat(mapper.readValue("\"Refused\"", FailureReason.class)).isEqualTo(FailureReason.REFUSED);
        assertThat(mapper.readValue("\"Damaged\"", FailureReason.class)).isEqualTo(FailureReason.DAMAGED);
        assertThat(mapper.readValue("\"Other\"", FailureReason.class)).isEqualTo(FailureReason.OTHER);
    }

    @Test
    void serializesBackToFrontendLabel() throws Exception {
        assertThat(mapper.writeValueAsString(FailureReason.CUSTOMER_ABSENT)).isEqualTo("\"Customer absent\"");
    }
}
