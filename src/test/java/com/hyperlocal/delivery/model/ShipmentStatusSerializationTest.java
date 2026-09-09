package com.hyperlocal.delivery.model;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ShipmentStatusSerializationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void allStatusesSerializeToSnakeCase() throws Exception {
        assertThat(mapper.writeValueAsString(ShipmentStatus.ASSIGNED)).isEqualTo("\"assigned\"");
        assertThat(mapper.writeValueAsString(ShipmentStatus.PICKED_UP)).isEqualTo("\"picked_up\"");
        assertThat(mapper.writeValueAsString(ShipmentStatus.IN_TRANSIT)).isEqualTo("\"in_transit\"");
        assertThat(mapper.writeValueAsString(ShipmentStatus.OUT_FOR_DELIVERY)).isEqualTo("\"out_for_delivery\"");
        assertThat(mapper.writeValueAsString(ShipmentStatus.DELIVERED)).isEqualTo("\"delivered\"");
        assertThat(mapper.writeValueAsString(ShipmentStatus.FAILED)).isEqualTo("\"failed\"");
        assertThat(mapper.writeValueAsString(ShipmentStatus.RETURNED)).isEqualTo("\"returned\"");
    }

    @Test
    void enumNameUnchangedForJpaPersistence() {
        assertThat(ShipmentStatus.PICKED_UP.name()).isEqualTo("PICKED_UP");
    }
}
