package com.hyperlocal.delivery.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hyperlocal.delivery.BaseIntegrationTest;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.User;

import jakarta.persistence.EntityManagerFactory;

/**
 * Regression test for the cartesian-product bug in
 * {@link ShipmentRepository#findWithDetailById}: fetch-joining both the
 * {@code events} list (a Hibernate "bag" -- unindexed, so it cannot be
 * deduplicated the way a fetch-joined {@code Set} can) and the
 * {@code attempts} collection in the same query multiplies the result rows
 * (events x attempts), so {@code events} ends up with each row duplicated
 * once per attempt.
 *
 * <p>Drives a shipment through two full lifecycle passes -- delivery attempt
 * failure, owner reassignment back to ASSIGNED, then a second run to
 * OUT_FOR_DELIVERY and a second failed attempt -- which is the one path in
 * this domain's state machine that produces more than one
 * {@link com.hyperlocal.delivery.model.DeliveryAttempt} on a single
 * shipment (see {@code ShipmentService.reassign}, which is the only way to
 * move a shipment out of the terminal-looking FAILED status back to
 * ASSIGNED for another delivery run).
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ShipmentRepositoryEntityGraphTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Business business;
    private User owner;
    private User agent;
    private String ownerToken;
    private String agentToken;

    @BeforeEach
    void setUp() {
        business = createAndSaveBusiness("EntityGraph Biz", "entitygraphbiz@test.com");
        owner = createAndSaveOwner(business, "entitygraphowner@test.com");
        agent = createAndSaveAgent(business, "entitygraphagent@test.com");
        ownerToken = tokenFor(owner);
        agentToken = tokenFor(agent);
    }

    private long createShipment() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerName": "EntityGraph Customer",
                                    "customerPhone": "+91-9000000099",
                                    "deliveryAddress": "99 EntityGraph Street"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("data").get("id").asLong();
    }

    private void driveToOutForDelivery(long shipmentId) throws Exception {
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/pickup")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/start-transit")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/out-for-delivery")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
    }

    private void failAttempt(long shipmentId, String reason, String notes) throws Exception {
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/fail")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + reason + "\",\"notes\":\"" + notes + "\"}"))
                .andExpect(status().isOk());
    }

    /**
     * Builds a shipment with 10 events and 2 delivery attempts (the only
     * realistic multi-attempt path this state machine allows: FAILED ->
     * reassign -> ASSIGNED -> drive to OUT_FOR_DELIVERY again -> fail
     * again), then asserts that {@code findWithDetailById} returns exactly
     * 10 events and 2 attempts -- not the cartesian-inflated 20 events a
     * combined fetch-join over both collections would otherwise produce.
     */
    @Test
    void findWithDetailById_doesNotInflateEventsAcrossAttempts() throws Exception {
        long shipmentId = createShipment();

        // Events so far: 1 (auto-assign on create)
        driveToOutForDelivery(shipmentId);
        // Events so far: 1 + 3 (pickup, transit, out-for-delivery) = 4
        failAttempt(shipmentId, "Customer absent", "no answer, attempt 1");
        // Events so far: 4 + 1 (fail) = 5; attempts so far: 1

        mockMvc.perform(post("/api/shipments/" + shipmentId + "/reassign")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\": " + agent.getId() + ", \"notes\": \"redispatching\"}"))
                .andExpect(status().isOk());
        // Events so far: 5 + 1 (reassign FAILED -> ASSIGNED) = 6

        driveToOutForDelivery(shipmentId);
        // Events so far: 6 + 3 = 9
        failAttempt(shipmentId, "Refused", "refused delivery, attempt 2");
        // Events so far: 9 + 1 (fail) = 10; attempts so far: 2

        flushAndClear();

        Shipment reloaded = shipmentRepository.findWithDetailById(shipmentId).orElseThrow();

        assertThat(reloaded.getAttempts())
                .as("two delivery attempts should have been recorded")
                .hasSize(2);
        assertThat(reloaded.getEvents())
                .as("events must not be duplicated by the cartesian product of the "
                        + "events x attempts fetch join -- expected exactly 10 distinct "
                        + "events, not 10 * 2 = 20")
                .hasSize(10);

        // Ordering must also survive: events.get(0) is the original auto-assign,
        // and the list must remain chronological (createdAt ASC), not
        // reshuffled or duplicated by the join.
        assertThat(reloaded.getEvents().get(0).getToStatus().name()).isEqualTo("ASSIGNED");
    }

    /**
     * Confirms the two-query split fix does not reintroduce the N+1 problem
     * the original combined {@code @EntityGraph} was written to solve: a
     * single {@code findWithDetailById} call must issue exactly two SELECTs
     * (one for the shipment + events + assignedAgent, one for attempts),
     * never one query per event or per attempt.
     */
    @Test
    void findWithDetailById_issuesExactlyTwoQueries_regardlessOfCollectionSize() throws Exception {
        long shipmentId = createShipment();
        driveToOutForDelivery(shipmentId);
        failAttempt(shipmentId, "Customer absent", "no answer, attempt 1");
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/reassign")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\": " + agent.getId() + "}"))
                .andExpect(status().isOk());
        driveToOutForDelivery(shipmentId);
        failAttempt(shipmentId, "Refused", "refused delivery, attempt 2");

        flushAndClear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        Shipment reloaded = shipmentRepository.findWithDetailById(shipmentId).orElseThrow();
        // Touch every association a DTO mapper would touch, to be sure no
        // further lazy-load queries fire outside the two we expect.
        reloaded.getEvents().forEach(e -> e.getChangedBy().getFullName());
        reloaded.getAttempts().forEach(a -> a.getAgent().getFullName());
        reloaded.getAssignedAgent().getFullName();

        assertThat(statistics.getPrepareStatementCount())
                .as("findWithDetailById should issue exactly 2 SELECTs (events+agent, then "
                        + "attempts+agent) no matter how many events/attempts exist -- not "
                        + "N+1 per collection element")
                .isEqualTo(2);
    }
}
