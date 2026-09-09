package com.hyperlocal.delivery.dto;

import tools.jackson.databind.ObjectMapper;
import com.hyperlocal.delivery.dto.shipment.AdvanceRequest;
import com.hyperlocal.delivery.dto.shipment.ReassignRequest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RequestFieldAliasTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void advanceRequestAcceptsSingularNoteKey() throws Exception {
        AdvanceRequest req = mapper.readValue("{\"note\":\"left at door\"}", AdvanceRequest.class);
        assertThat(req.notes()).isEqualTo("left at door");
    }

    @Test
    void reassignRequestAcceptsSingularNoteKey() throws Exception {
        ReassignRequest req = mapper.readValue("{\"agentId\":5,\"note\":\"agent sick\"}", ReassignRequest.class);
        assertThat(req.notes()).isEqualTo("agent sick");
        assertThat(req.agentId()).isEqualTo(5L);
    }
}
