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
import com.hyperlocal.delivery.model.DeliveryAttempt;
import com.hyperlocal.delivery.model.FailureReason;
import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.ShipmentEvent;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.repository.DeliveryAttemptRepository;
import com.hyperlocal.delivery.repository.ShipmentEventRepository;
import com.hyperlocal.delivery.repository.ShipmentRepository;

/**
 * Regression coverage for the second occurrence of the {@code
 * LazyInitializationException} bug class first fixed in
 * {@link ReportControllerRegisterLazyInitIntegrationTest} (that fix covered
 * {@code GET /api/reports/register}'s {@code assignedAgent} association;
 * this covers a different gap found in the comprehensive sweep that
 * followed).
 *
 * <p>{@code GET /api/reports/register/{token}} loads its shipment via {@code
 * ShipmentService#getByTokenInBusiness}, which calls {@code
 * ShipmentRepository#findWithDetailByTrackingToken}. That query's {@code
 * @EntityGraph} fetched the {@code events} and {@code attempts} collections
 * themselves, but not each event's nested {@code changedBy} user or each
 * attempt's nested {@code agent} user. {@code RegisterInspectDto.from()} is
 * built in {@code ReportController}, outside {@code ShipmentService}'s
 * {@code @Transactional} boundary (open-in-view is disabled), and delegates
 * to {@code ShipmentEventDto.from()} / {@code DeliveryAttemptDto.from()},
 * both of which touch those associations — so the request threw a 500
 * {@code LazyInitializationException} the moment a shipment had at least one
 * event or attempt.
 *
 * <p>Deliberately its own test class, following the same pattern as {@link
 * ReportControllerRegisterLazyInitIntegrationTest}: {@code
 * ReportControllerIntegrationTest}'s shared {@code @BeforeEach} runs inside
 * {@link BaseIntegrationTest}'s class-level rollback-only {@code
 * @Transactional}, which keeps one Hibernate session open across an entire
 * test method (including its MockMvc call) and would mask this exact bug.
 * This test opts out via {@code @Transactional(propagation = NOT_SUPPORTED)}
 * so the request genuinely runs under closed transaction boundaries, like
 * production.
 */
@ActiveProfiles("mysql-test")
class ReportControllerRegisterInspectLazyInitIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ShipmentEventRepository shipmentEventRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

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
            deliveryAttemptRepository.deleteAll(deliveryAttemptRepository.findByShipment_IdOrderByAttemptedAtAsc(shipment.getId()));
            shipmentEventRepository.deleteAll(shipmentEventRepository.findAll().stream()
                    .filter(e -> e.getShipment().getId().equals(shipment.getId()))
                    .toList());
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
    void registerInspectPopulatesEventAndAttemptUsersOutsideAnyWrappingTestTransaction() throws Exception {
        business = createAndSaveBusiness("Inspect Lazy Init Biz", "inspectlazyinitbiz@test.com");
        owner = createAndSaveOwner(business, "inspectlazyinitowner@test.com");
        agent = createAndSaveAgent(business, "inspectlazyinitagent@test.com");
        shipment = shipmentRepository.save(Shipment.builder()
                .trackingToken("inspect-lazy-init-track-0")
                .business(business)
                .assignedAgent(agent)
                .status(ShipmentStatus.FAILED)
                .customerName("Inspect Lazy Init Customer")
                .customerPhone("+91-9000000097")
                .deliveryAddress("1 Inspect Lazy Init St")
                .build());

        shipmentEventRepository.save(ShipmentEvent.builder()
                .shipment(shipment)
                .fromStatus(null)
                .toStatus(ShipmentStatus.ASSIGNED)
                .changedBy(owner)
                .notes("Auto-assigned to agent: " + agent.getFullName())
                .build());

        deliveryAttemptRepository.save(DeliveryAttempt.builder()
                .shipment(shipment)
                .agent(agent)
                .attemptNumber(1)
                .failureReason(FailureReason.CUSTOMER_ABSENT)
                .notes("Nobody home")
                .build());

        String ownerToken = tokenFor(owner);

        mockMvc.perform(get("/api/reports/register/" + shipment.getTrackingToken())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[0].changedBy", Matchers.equalTo(owner.getFullName())))
                .andExpect(jsonPath("$.data.attempts[0].agentName", Matchers.equalTo(agent.getFullName())));
    }
}
