package com.hyperlocal.delivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.hyperlocal.delivery.BaseIntegrationTest;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.DeliveryAttempt;
import com.hyperlocal.delivery.model.FailureReason;
import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.model.ShipmentEvent;
import com.hyperlocal.delivery.repository.DeliveryAttemptRepository;
import com.hyperlocal.delivery.repository.ShipmentEventRepository;
import com.hyperlocal.delivery.repository.ShipmentRepository;

/**
 * Integration tests for the {@code /api/reports/*} surface (task 13).
 * Runs against MySQL (native queries use TIMESTAMPDIFF), mirroring
 * {@link AnalyticsIntegrationTest} which backs the underlying analytics.
 */
@ActiveProfiles("mysql-test")
class ReportControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private ShipmentEventRepository shipmentEventRepository;

    private Business business;
    private User owner;
    private User agent;
    private String ownerToken;
    private String agentToken;
    private Shipment shipment;

    @BeforeEach
    void setUp() {
        business = createAndSaveBusiness("Reports Biz", "reportsbiz@test.com");
        owner = createAndSaveOwner(business, "reportsowner@test.com");
        agent = createAndSaveAgent(business, "reportsagent@test.com");
        ownerToken = tokenFor(owner);
        agentToken = tokenFor(agent);

        for (int i = 0; i < 5; i++) {
            Shipment shipment = Shipment.builder()
                    .trackingToken("reports-track-" + i)
                    .business(business)
                    .assignedAgent(agent)
                    .status(i < 3 ? ShipmentStatus.DELIVERED : ShipmentStatus.FAILED)
                    .customerName("Customer " + i)
                    .customerPhone("+91-900000000" + i)
                    .deliveryAddress(i + " Reports St")
                    .build();
            shipment = shipmentRepository.save(shipment);

            if (shipment.getStatus() == ShipmentStatus.FAILED) {
                DeliveryAttempt attempt = DeliveryAttempt.builder()
                        .shipment(shipment)
                        .agent(agent)
                        .failureReason(FailureReason.CUSTOMER_ABSENT)
                        .build();
                deliveryAttemptRepository.save(attempt);
            }

            if (i == 0) {
                this.shipment = shipment;
                ShipmentEvent event = ShipmentEvent.builder()
                        .shipment(shipment)
                        .fromStatus(null)
                        .toStatus(ShipmentStatus.ASSIGNED)
                        .changedBy(owner)
                        .notes("Auto-assigned to agent: " + agent.getFullName())
                        .build();
                shipmentEventRepository.save(event);
            }
        }

        flushAndClear();
    }

    @Test
    void overviewReturnsFrontendShapeForRangeParam() throws Exception {
        mockMvc.perform(get("/api/reports/overview?range=30").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").exists())
                .andExpect(jsonPath("$.data.deliveredPct").exists())
                .andExpect(jsonPath("$.data.onTimeRate").exists())
                .andExpect(jsonPath("$.data.avgDeliveryHours").exists())
                .andExpect(jsonPath("$.data.firstAttemptRate").exists())
                .andExpect(jsonPath("$.data.days").isArray())
                .andExpect(jsonPath("$.data.agentBars").isArray())
                .andExpect(jsonPath("$.data.reasons").isArray())
                .andExpect(jsonPath("$.data.reasons[0].label").exists())
                .andExpect(jsonPath("$.data.reasons[0].label", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.equalTo("CUSTOMER_ABSENT"))));
    }

    /**
     * Flow audit 2026-09-10 (docs/audits/2026-09-10-flow-reports-analytics.md):
     * {@code firstAttemptSuccessRaw} used to LEFT JOIN delivery_attempts
     * without deduplicating, so a delivered shipment with N recorded
     * attempts fanned out to N joined rows and inflated the denominator —
     * silently understating the reported rate. This fixture creates one
     * DELIVERED shipment with zero attempts and one DELIVERED shipment with
     * TWO attempts. Combined with the 3 zero-attempt DELIVERED shipments
     * already seeded in {@code setUp()}, the correct rate is 4 first-attempt
     * successes of 5 total delivered (80%) — the pre-fix fan-out bug would
     * have reported 4 of 6 join-fanned-out rows (66.7%) instead, since the
     * two-attempt shipment alone contributed 2 rows to the join.
     */
    @Test
    void overview_firstAttemptRate_notInflatedByMultipleAttemptsOnOneShipment() throws Exception {
        Shipment cleanDelivery = shipmentRepository.save(Shipment.builder()
                .trackingToken("reports-fan-out-clean")
                .business(business)
                .assignedAgent(agent)
                .status(ShipmentStatus.DELIVERED)
                .customerName("Clean Delivery Customer")
                .customerPhone("+91-9000001001")
                .deliveryAddress("1 Fan-out Street")
                .build());

        Shipment multiAttemptDelivery = shipmentRepository.save(Shipment.builder()
                .trackingToken("reports-fan-out-multi")
                .business(business)
                .assignedAgent(agent)
                .status(ShipmentStatus.DELIVERED)
                .customerName("Multi Attempt Customer")
                .customerPhone("+91-9000001002")
                .deliveryAddress("2 Fan-out Street")
                .build());
        deliveryAttemptRepository.save(DeliveryAttempt.builder()
                .shipment(multiAttemptDelivery)
                .agent(agent)
                .attemptNumber(1)
                .failureReason(FailureReason.CUSTOMER_ABSENT)
                .build());
        deliveryAttemptRepository.save(DeliveryAttempt.builder()
                .shipment(multiAttemptDelivery)
                .agent(agent)
                .attemptNumber(2)
                .failureReason(FailureReason.CUSTOMER_ABSENT)
                .build());
        flushAndClear();

        mockMvc.perform(get("/api/reports/overview?range=30").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstAttemptRate").value(
                        org.hamcrest.Matchers.closeTo(80.0, 0.5)));

        assertThat(cleanDelivery.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    }

    @Test
    void trendReturnsRowsForDaysParam() throws Exception {
        mockMvc.perform(get("/api/reports/trend?days=14").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows").isArray())
                .andExpect(jsonPath("$.data.rows[0].date").exists())
                .andExpect(jsonPath("$.data.rows[0].progress").exists());
    }

    @Test
    void agentPerformanceReturnsFlatArrayNotWrappedObject() throws Exception {
        mockMvc.perform(get("/api/reports/agent-performance?range=30").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].avgHours").exists())
                .andExpect(jsonPath("$.data[0].perDay").exists())
                .andExpect(jsonPath("$.data[0].active").exists());
    }

    @Test
    void agentRoleGetsForbiddenOnOverview() throws Exception {
        mockMvc.perform(get("/api/reports/overview?range=7").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentRoleGetsForbiddenOnTrend() throws Exception {
        mockMvc.perform(get("/api/reports/trend?days=7").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentRoleGetsForbiddenOnAgentPerformance() throws Exception {
        mockMvc.perform(get("/api/reports/agent-performance?range=7").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerListSupportsCommaJoinedStatusFilter() throws Exception {
        mockMvc.perform(get("/api/reports/register?status=delivered,failed").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].token").exists())
                .andExpect(jsonPath("$.data[0].agentName").exists());
    }

    /**
     * Final-review Finding 1/3 regression: an invalid {@code status} value
     * must surface as a 400 (matching {@code ShipmentController.list}'s
     * registered {@code ShipmentStatusConverter} behavior for the same
     * enum), not 500 via the generic exception fallback.
     */
    @Test
    void registerListWithInvalidStatusReturns400NotServerError() throws Exception {
        mockMvc.perform(get("/api/reports/register?status=not_a_real_status")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerExportWithInvalidStatusReturns400NotServerError() throws Exception {
        mockMvc.perform(get("/api/reports/register/export?status=not_a_real_status")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerInspectReturnsEventsWithTimeAndLabel() throws Exception {
        mockMvc.perform(get("/api/reports/register/" + shipment.getTrackingToken()).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[0].time").exists())
                .andExpect(jsonPath("$.data.events[0].label").exists())
                .andExpect(jsonPath("$.data.attempts").exists());
    }

    @Test
    void registerInspectReturns404ForCrossTenantToken() throws Exception {
        Business otherBusiness = createAndSaveBusiness("Other Biz", "otherbiz@test.com");
        User otherAgent = createAndSaveAgent(otherBusiness, "otheragent@test.com");
        Shipment otherShipment = Shipment.builder()
                .trackingToken("other-biz-track-0")
                .business(otherBusiness)
                .assignedAgent(otherAgent)
                .status(ShipmentStatus.ASSIGNED)
                .customerName("Other Customer")
                .customerPhone("+91-9000000099")
                .deliveryAddress("99 Other St")
                .build();
        shipmentRepository.save(otherShipment);
        flushAndClear();

        mockMvc.perform(get("/api/reports/register/" + otherShipment.getTrackingToken())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void agentRoleGetsForbiddenOnRegister() throws Exception {
        mockMvc.perform(get("/api/reports/register").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentRoleGetsForbiddenOnRegisterInspect() throws Exception {
        mockMvc.perform(get("/api/reports/register/" + shipment.getTrackingToken())
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentPerformanceExportReturnsCsvContentType() throws Exception {
        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(
                        get("/api/reports/agent-performance/export?range=30")
                                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).startsWith("id,name,active,assigned,delivered,failed,returned,open,avgHours,perDay");
    }

    @Test
    void registerExportReturnsCsvContentType() throws Exception {
        mockMvc.perform(get("/api/reports/register/export?status=delivered")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")));
    }

    @Test
    void unknownExportKindReturns400() throws Exception {
        mockMvc.perform(get("/api/reports/bogus/export").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agentRoleGetsForbiddenOnExport() throws Exception {
        mockMvc.perform(get("/api/reports/agent-performance/export?range=7")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    /**
     * Task 18 regression: reporting must stay read-only per the plan's
     * Global Constraints. Rather than trusting that no one adds a mutating
     * endpoint later, reflect over every declared method on
     * {@link ReportController} and fail if any is mapped to anything but
     * GET (no {@code @PostMapping}/{@code @PutMapping}/
     * {@code @PatchMapping}/{@code @DeleteMapping}, and no
     * {@code @RequestMapping} naming a non-GET method).
     */
    @Test
    void noReportControllerMethodIsAnythingButGet() {
        for (Method method : com.hyperlocal.delivery.controller.ReportController.class.getDeclaredMethods()) {
            assertThat(method.isAnnotationPresent(PostMapping.class))
                    .as("method %s must not be @PostMapping", method.getName())
                    .isFalse();
            assertThat(method.isAnnotationPresent(PutMapping.class))
                    .as("method %s must not be @PutMapping", method.getName())
                    .isFalse();
            assertThat(method.isAnnotationPresent(PatchMapping.class))
                    .as("method %s must not be @PatchMapping", method.getName())
                    .isFalse();
            assertThat(method.isAnnotationPresent(DeleteMapping.class))
                    .as("method %s must not be @DeleteMapping", method.getName())
                    .isFalse();

            if (method.isAnnotationPresent(RequestMapping.class)) {
                RequestMapping mapping = method.getAnnotation(RequestMapping.class);
                for (RequestMethod requestMethod : mapping.method()) {
                    assertThat(requestMethod)
                            .as("method %s's @RequestMapping must only allow GET", method.getName())
                            .isEqualTo(RequestMethod.GET);
                }
            }
        }
    }
}
