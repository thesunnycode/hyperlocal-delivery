package com.hyperlocal.delivery.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hyperlocal.delivery.dto.attempt.CreateAttemptRequest;
import com.hyperlocal.delivery.dto.shipment.DeliveryAttemptDto;
import com.hyperlocal.delivery.dto.shipment.ShipmentResponseDto;
import com.hyperlocal.delivery.exception.InvalidStateForAttemptException;
import com.hyperlocal.delivery.exception.ShipmentNotFoundException;
import com.hyperlocal.delivery.model.DeliveryAttempt;
import com.hyperlocal.delivery.model.FailureReason;
import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.ShipmentEvent;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.model.UserRole;
import com.hyperlocal.delivery.repository.DeliveryAttemptRepository;
import com.hyperlocal.delivery.repository.ShipmentEventRepository;
import com.hyperlocal.delivery.repository.ShipmentRepository;
import com.hyperlocal.delivery.repository.UserRepository;
import com.hyperlocal.delivery.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * Business logic for recording delivery attempts. Each attempt atomically
 * inserts the attempt record, transitions the shipment to FAILED (or
 * RETURNED once {@link #MAX_ATTEMPTS} is reached), and inserts the
 * corresponding event.
 */
@Service
@RequiredArgsConstructor
public class DeliveryAttemptService {

    /**
     * Attempts beyond this count return the shipment to the store rather
     * than staying open for another try — matching the agent app's own
     * copy ("After the third, it goes back to the store."). Confirmed via
     * a live flow audit (docs/audits/2026-09-10-flow-delivery-attempts.md)
     * that this was NOT previously enforced: a shipment could accumulate
     * unbounded failed attempts and never autonomously reach RETURNED.
     */
    private static final int MAX_ATTEMPTS = 3;

    private final ShipmentRepository shipmentRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final ShipmentEventRepository shipmentEventRepository;
    private final UserRepository userRepository;

    /**
     * Record a failed delivery attempt. Atomically:
     * 1. Validates the shipment is OUT_FOR_DELIVERY and the caller is the assigned agent
     * 2. Inserts a delivery_attempts row
     * 3. Transitions the shipment to FAILED
     * 4. Inserts a shipment_events row
     *
     * <p>Locks the shipment row first (before any other check), both to
     * close the {@code /deliver}-vs-{@code /attempt} race — two concurrent
     * transitions off the same OUT_FOR_DELIVERY read can't both commit — and
     * because an unscoped lookup followed by a 403 for the wrong-agent case
     * would let a caller distinguish "shipment exists but isn't mine" (403)
     * from "shipment doesn't exist" (404) across tenants; both cases now
     * return 404, matching every other action endpoint.
     */
    @Transactional
    public DeliveryAttemptDto record(CustomUserDetails actor, Long shipmentId, CreateAttemptRequest req) {
        DeliveryAttempt attempt = recordInternal(actor, shipmentId, req.failureReason(), req.notes());
        return DeliveryAttemptDto.from(attempt);
    }

    /**
     * Same operation as {@link #record}, exposed for {@code POST
     * /api/shipments/{id}/fail} (the frontend's action-endpoint contract),
     * which expects the full {@link ShipmentResponseDto} shape back rather
     * than the bare attempt — matching every other agent action endpoint
     * in {@code ShipmentController}.
     */
    @Transactional
    public ShipmentResponseDto recordFailure(
            CustomUserDetails actor, Long shipmentId, FailureReason reason, String notes) {
        DeliveryAttempt attempt = recordInternal(actor, shipmentId, reason, notes);
        Shipment loaded = shipmentRepository.findWithDetailById(attempt.getShipment().getId())
                .orElse(attempt.getShipment());
        return ShipmentResponseDto.from(loaded);
    }

    /**
     * Shared core of {@link #record} and {@link #recordFailure}: locks the
     * shipment, checks agent ownership (404, never 403), checks
     * OUT_FOR_DELIVERY, appends exactly one immutable {@link DeliveryAttempt}
     * row via {@code repository.save()} on a freshly built entity (never an
     * update to an existing attempt), transitions the shipment to FAILED
     * (or RETURNED on the {@link #MAX_ATTEMPTS}th attempt), and appends one
     * {@link ShipmentEvent} row.
     */
    private DeliveryAttempt recordInternal(
            CustomUserDetails actor, Long shipmentId, FailureReason reason, String notes) {
        Shipment shipment = shipmentRepository.lockById(shipmentId)
                .orElseThrow(() -> new ShipmentNotFoundException(shipmentId));

        // Verify the caller is the assigned agent. Never reveals that a
        // shipment exists to an agent who isn't assigned to it.
        if (shipment.getAssignedAgent() == null ||
                !shipment.getAssignedAgent().getId().equals(actor.getUserId())) {
            throw new ShipmentNotFoundException(shipmentId);
        }

        // Verify shipment is in OUT_FOR_DELIVERY status
        if (shipment.getStatus() != ShipmentStatus.OUT_FOR_DELIVERY) {
            throw new InvalidStateForAttemptException(shipment.getStatus());
        }

        // Calculate attempt number
        long count = deliveryAttemptRepository.countByShipment_Id(shipmentId);
        int attemptNumber = (int) count + 1;

        // Insert delivery attempt
        DeliveryAttempt attempt = DeliveryAttempt.builder()
                .shipment(shipment)
                .agent(userRepository.getReferenceById(actor.getUserId()))
                .attemptNumber(attemptNumber)
                .failureReason(reason)
                .notes(notes)
                .build();
        attempt = deliveryAttemptRepository.save(attempt);

        // The Nth attempt (MAX_ATTEMPTS) sends the shipment back to the
        // store instead of leaving it open for yet another try.
        ShipmentStatus newStatus = attemptNumber >= MAX_ATTEMPTS
                ? ShipmentStatus.RETURNED
                : ShipmentStatus.FAILED;
        shipment.setStatus(newStatus);
        shipmentRepository.save(shipment);

        // Insert event
        ShipmentEvent event = ShipmentEvent.builder()
                .shipment(shipment)
                .fromStatus(ShipmentStatus.OUT_FOR_DELIVERY)
                .toStatus(newStatus)
                .changedBy(userRepository.getReferenceById(actor.getUserId()))
                .notes(notes)
                .build();
        shipmentEventRepository.save(event);

        return attempt;
    }

    /**
     * List all delivery attempts for a shipment. The owner may read any
     * shipment in their business; the assigned agent may read only their
     * own shipment. Both denial paths return 404, never 403 — matching the
     * posture of every other endpoint in this service.
     */
    @Transactional(readOnly = true)
    public List<DeliveryAttemptDto> listForShipment(CustomUserDetails actor, Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ShipmentNotFoundException(shipmentId));

        boolean allowed = actor.getRole() == UserRole.BUSINESS_OWNER
                ? shipment.getBusiness().getId().equals(actor.getBusinessId())
                : shipment.getAssignedAgent() != null
                        && shipment.getAssignedAgent().getId().equals(actor.getUserId());

        if (!allowed) {
            throw new ShipmentNotFoundException(shipmentId);
        }

        return deliveryAttemptRepository.findByShipment_IdOrderByAttemptedAtAsc(shipmentId)
                .stream()
                .map(DeliveryAttemptDto::from)
                .toList();
    }
}
