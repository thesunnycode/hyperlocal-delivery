package com.hyperlocal.delivery.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hyperlocal.delivery.BaseIntegrationTest;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.repository.ShipmentRepository;

/**
 * Regression coverage for a bug found via manual E2E verification of the
 * admin Register page (not part of the original task plan): {@code GET
 * /api/reports/register} threw a 500 {@code LazyInitializationException}
 * touching {@code assignedAgent}, because {@code
 * ShipmentService#searchForRegister} loaded shipments via a plain {@code
 * findAll(Specification, Pageable)} with no eager fetch of the association,
 * and the app runs with {@code spring.jpa.open-in-view: false} — so by the
 * time {@code RegisterRowDto.from(shipment)} called {@code
 * shipment.getAssignedAgent().getFullName()} in the controller, outside the
 * service's transactional boundary, Hibernate had no session left to
 * initialize the proxy with.
 *
 * <p>This is deliberately its own test class rather than an added method on
 * {@link ReportControllerIntegrationTest}: that class's shared {@code
 * @BeforeEach} runs inside {@link BaseIntegrationTest}'s class-level
 * rollback-only {@code @Transactional}, which keeps one Hibernate session
 * open across an entire test method — including its MockMvc call — and
 * would silently mask this exact bug (a test asserting only that {@code
 * agentName} is present, not correctly populated, would pass whether or not
 * the fetch was eager). Splitting this into its own class lets the single
 * test method opt out of that wrapping transaction via {@code
 * @Transactional(propagation = NOT_SUPPORTED)} without disturbing the other
 * class's shared fixture, so the request genuinely runs under closed
 * transaction boundaries like production.
 */
@ActiveProfiles("mysql-test")
class ReportControllerRegisterLazyInitIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ShipmentRepository shipmentRepository;

    private Business business;
    private User owner;
    private User agent;
    private Shipment shipment;

    /**
     * Cleans up manually: with the class-level rollback-only transaction
     * suspended for the test method (see the class Javadoc), nothing
     * auto-rolls-back the data this test commits.
     */
    @AfterEach
    void tearDown() {
        if (shipment != null) {
            shipmentRepository.deleteById(shipment.getId());
        }
        if (agent != null) {
            userRepository.deleteById(agent.getId());
        }
        if (owner != null) {
            userRepository.deleteById(owner.getId());
        }
        if (business != null) {
            businessRepository.deleteById(business.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void registerListPopulatesAgentNameOutsideAnyWrappingTestTransaction() throws Exception {
        business = createAndSaveBusiness("Lazy Init Biz", "lazyinitbiz@test.com");
        owner = createAndSaveOwner(business, "lazyinitowner@test.com");
        agent = createAndSaveAgent(business, "lazyinitagent@test.com");
        shipment = shipmentRepository.save(Shipment.builder()
                .trackingToken("lazy-init-track-0")
                .business(business)
                .assignedAgent(agent)
                .status(ShipmentStatus.ASSIGNED)
                .customerName("Lazy Init Customer")
                .customerPhone("+91-9000000098")
                .deliveryAddress("1 Lazy Init St")
                .build());

        String ownerToken = tokenFor(owner);

        mockMvc.perform(get("/api/reports/register?agentId=" + agent.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].agentName", Matchers.equalTo(agent.getFullName())));
    }
}
