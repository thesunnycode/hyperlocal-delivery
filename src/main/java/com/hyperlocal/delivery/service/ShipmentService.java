package com.hyperlocal.delivery.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hyperlocal.delivery.dto.shipment.CreateShipmentRequest;
import com.hyperlocal.delivery.dto.shipment.ReassignRequest;
import com.hyperlocal.delivery.dto.shipment.ShipmentResponseDto;
import com.hyperlocal.delivery.dto.shipment.ShipmentSummaryDto;
import com.hyperlocal.delivery.dto.tracking.PublicTrackingResponse;
import com.hyperlocal.delivery.exception.InvalidAgentException;
import com.hyperlocal.delivery.exception.InvalidStateTransitionException;
import com.hyperlocal.delivery.exception.ShipmentNotFoundException;
import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.ShipmentEvent;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.model.ShipmentStatusSets;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.model.UserRole;
import com.hyperlocal.delivery.repository.BusinessRepository;
import com.hyperlocal.delivery.repository.ShipmentEventRepository;
import com.hyperlocal.delivery.repository.ShipmentRepository;
import com.hyperlocal.delivery.repository.ShipmentSpecifications;
import com.hyperlocal.delivery.repository.UserRepository;
import com.hyperlocal.delivery.security.CustomUserDetails;
import com.hyperlocal.delivery.util.TrackingTokenGenerator;

import lombok.RequiredArgsConstructor;

/**
 * Core business logic for shipment lifecycle management including creation,
 * status transitions, reassignment, and public tracking.
 */
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentEventRepository shipmentEventRepository;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final AgentAssignmentService agentAssignmentService;
    private final TrackingTokenGenerator trackingTokenGenerator;

    private static final Set<ShipmentStatus> NON_TERMINAL = ShipmentStatusSets.NON_TERMINAL_FOR_REASSIGNMENT;

    /**
     * Create a new shipment with auto-assignment to the least-loaded agent.
     */
    @Transactional
    public ShipmentResponseDto create(Long businessId, Long actorUserId, CreateShipmentRequest req) {
        User agent = agentAssignmentService.pickAndLockLeastLoadedAgent(businessId);
        User actor = userRepository.getReferenceById(actorUserId);

        Shipment shipment = Shipment.builder()
                .trackingToken(trackingTokenGenerator.generate())
                .business(businessRepository.getReferenceById(businessId))
                .assignedAgent(agent)
                .status(ShipmentStatus.ASSIGNED)
                .customerName(req.customerName())
                .customerPhone(req.customerPhone())
                .deliveryAddress(req.deliveryAddress())
                .scheduledDeliveryAt(req.scheduledDeliveryAt())
                .build();

        shipment = shipmentRepository.save(shipment);

        // Single system event: a shipment starts life already ASSIGNED — there
        // is no CREATED status, per STATE_MACHINE.md.
        ShipmentEvent assignEvent = ShipmentEvent.builder()
                .shipment(shipment)
                .fromStatus(null)
                .toStatus(ShipmentStatus.ASSIGNED)
                .changedBy(actor)
                .notes("Auto-assigned to agent: " + agent.getFullName())
                .build();
        shipmentEventRepository.save(assignEvent);

        // Not re-fetched via findWithDetailById: `shipment` here is a
        // transient-origin entity (built via Shipment.builder(), never
        // loaded from the DB), so its `events` field is still null rather
        // than an initialized Hibernate collection proxy. A re-fetch inside
        // this same persistence context returns this exact managed
        // instance unchanged — the entity-graph JOIN does not retroactively
        // populate a null mappedBy field on an already-managed entity — so
        // the just-saved event silently never appeared in this response
        // (though it was already correctly persisted; any later GET showed
        // it). Setting the collections directly is what the response
        // actually needs: exactly one event (this one) and zero attempts,
        // both already known without another query.
        shipment.setEvents(List.of(assignEvent));
        shipment.setAttempts(Set.of());
        return ShipmentResponseDto.from(shipment);
    }

    /**
     * Shared implementation for every single-step agent transition. Verifies
     * the caller is the shipment's assigned agent unconditionally — this is
     * the fix for the bug in the old {@code updateStatus()}, where that check
     * only ran in a branch that was unreachable in practice. Never reveals
     * that a shipment exists to an agent who isn't assigned to it.
     *
     * <p>Acquires the row lock as the very first DB touch so two concurrent
     * transitions off the same starting status (e.g. two {@code /deliver}
     * calls, or a {@code /deliver} racing a {@code /attempt}) serialize
     * instead of both reading the pre-transition status and both committing —
     * which would otherwise leave a contradictory terminal state.
     */
    private ShipmentResponseDto advance(
            CustomUserDetails actor, Long shipmentId,
            ShipmentStatus requiredFrom, ShipmentStatus to, String notes) {
        Shipment shipment = shipmentRepository.lockById(shipmentId)
                .orElseThrow(() -> new ShipmentNotFoundException(shipmentId));

        if (shipment.getAssignedAgent() == null
                || !shipment.getAssignedAgent().getId().equals(actor.getUserId())) {
            throw new ShipmentNotFoundException(shipmentId);
        }

        ShipmentStatus fromStatus = shipment.getStatus();
        if (fromStatus != requiredFrom) {
            throw new InvalidStateTransitionException(fromStatus, to);
        }

        shipment.setStatus(to);
        if (to == ShipmentStatus.DELIVERED) {
            shipment.setDeliveredAt(LocalDateTime.now());
        }
        shipment = shipmentRepository.save(shipment);

        ShipmentEvent event = ShipmentEvent.builder()
                .shipment(shipment)
                .fromStatus(fromStatus)
                .toStatus(to)
                .changedBy(userRepository.getReferenceById(actor.getUserId()))
                .notes(notes)
                .build();
        shipmentEventRepository.save(event);

        Shipment loaded = shipmentRepository.findWithDetailById(shipment.getId())
                .orElse(shipment);
        return ShipmentResponseDto.from(loaded);
    }

    /** Agent action: {@code ASSIGNED → PICKED_UP}. */
    @Transactional
    public ShipmentResponseDto pickup(CustomUserDetails actor, Long shipmentId, String notes) {
        return advance(actor, shipmentId, ShipmentStatus.ASSIGNED, ShipmentStatus.PICKED_UP, notes);
    }

    /** Agent action: {@code PICKED_UP → IN_TRANSIT}. */
    @Transactional
    public ShipmentResponseDto transit(CustomUserDetails actor, Long shipmentId, String notes) {
        return advance(actor, shipmentId, ShipmentStatus.PICKED_UP, ShipmentStatus.IN_TRANSIT, notes);
    }

    /** Agent action: {@code IN_TRANSIT → OUT_FOR_DELIVERY}. */
    @Transactional
    public ShipmentResponseDto outForDelivery(CustomUserDetails actor, Long shipmentId, String notes) {
        return advance(actor, shipmentId, ShipmentStatus.IN_TRANSIT, ShipmentStatus.OUT_FOR_DELIVERY, notes);
    }

    /** Agent action: {@code OUT_FOR_DELIVERY → DELIVERED} (terminal). */
    @Transactional
    public ShipmentResponseDto deliver(CustomUserDetails actor, Long shipmentId, String notes) {
        return advance(actor, shipmentId, ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED, notes);
    }

    /** Agent action: {@code OUT_FOR_DELIVERY → RETURNED} (terminal). */
    @Transactional
    public ShipmentResponseDto returnShipment(CustomUserDetails actor, Long shipmentId, String notes) {
        return advance(actor, shipmentId, ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.RETURNED, notes);
    }

    /**
     * Paginated listing of shipments with optional filters.
     */
    @Transactional(readOnly = true)
    public Page<ShipmentSummaryDto> list(Long businessId, ShipmentStatus status,
                                          Long agentId, LocalDate from, LocalDate to,
                                          Pageable pageable) {
        Specification<Shipment> spec = Specification.where(ShipmentSpecifications.belongsToBusiness(businessId));

        if (status != null) {
            spec = spec.and(ShipmentSpecifications.hasStatus(status));
        }
        if (agentId != null) {
            spec = spec.and(ShipmentSpecifications.assignedToAgent(agentId));
        }
        if (from != null) {
            spec = spec.and(ShipmentSpecifications.createdAfter(from));
        }
        if (to != null) {
            spec = spec.and(ShipmentSpecifications.createdBefore(to));
        }

        Page<Shipment> page = shipmentRepository.findAll(spec, pageable);
        return page.map(ShipmentSummaryDto::from);
    }

    /**
     * Get full shipment detail including events and attempts.
     */
    @Transactional(readOnly = true)
    public ShipmentResponseDto get(Long businessId, Long id) {
        Shipment shipment = shipmentRepository.findWithDetailById(id)
                .orElseThrow(() -> new ShipmentNotFoundException(id));
        if (!shipment.getBusiness().getId().equals(businessId)) {
            throw new ShipmentNotFoundException(id);
        }
        return ShipmentResponseDto.from(shipment);
    }

    /**
     * Agent-scoped detail lookup. Returns the full shipment only when the
     * caller is the assigned agent; 404 (never 403) otherwise, so this
     * never reveals a shipment's existence to an agent it isn't assigned
     * to — the same posture every action endpoint in this service already
     * takes.
     */
    @Transactional(readOnly = true)
    public ShipmentResponseDto getMyDetail(Long agentUserId, Long id) {
        Shipment shipment = shipmentRepository.findWithDetailById(id)
                .orElseThrow(() -> new ShipmentNotFoundException(id));
        if (shipment.getAssignedAgent() == null
                || !shipment.getAssignedAgent().getId().equals(agentUserId)) {
            throw new ShipmentNotFoundException(id);
        }
        return ShipmentResponseDto.from(shipment);
    }

    /**
     * The owner's two mutations, on one endpoint: reassign the agent on any
     * non-terminal shipment (status unchanged), and — only when the current
     * status is FAILED — also flip it back to ASSIGNED. Terminal shipments
     * (DELIVERED/RETURNED) can never be reassigned.
     */
    @Transactional
    public ShipmentResponseDto reassign(Long businessId, Long actorUserId, Long shipmentId, ReassignRequest req) {
        // Lock the shipment row first to prevent concurrent advance() or
        // recordInternal() from racing with this reassignment — otherwise
        // an agent delivering at the same instant could have their status
        // change silently overwritten by the reassignment's save().
        Shipment shipment = shipmentRepository.lockById(shipmentId)
                .orElseThrow(() -> new ShipmentNotFoundException(shipmentId));
        if (!shipment.getBusiness().getId().equals(businessId)) {
            throw new ShipmentNotFoundException(shipmentId);
        }

        ShipmentStatus currentStatus = shipment.getStatus();
        if (!NON_TERMINAL.contains(currentStatus)) {
            throw new InvalidStateTransitionException(currentStatus, ShipmentStatus.ASSIGNED);
        }

        User newAgent;
        if (req.agentId() != null) {
            newAgent = userRepository.findByIdAndBusiness_IdAndDeletedAtIsNull(req.agentId(), businessId)
                    .orElseThrow(() -> new InvalidAgentException("Agent not found: " + req.agentId()));
            if (newAgent.getRole() != UserRole.DELIVERY_AGENT) {
                throw new InvalidAgentException("User is not a delivery agent: " + req.agentId());
            }
            if (!Boolean.TRUE.equals(newAgent.getIsActive())) {
                throw new InvalidAgentException("Agent is not active: " + req.agentId());
            }
        } else {
            newAgent = agentAssignmentService.pickAndLockLeastLoadedAgent(businessId);
        }

        shipment.setAssignedAgent(newAgent);

        boolean wasFailed = currentStatus == ShipmentStatus.FAILED;
        if (wasFailed) {
            shipment.setStatus(ShipmentStatus.ASSIGNED);
        }
        shipment = shipmentRepository.save(shipment);

        // Both mutation paths are audited: the FAILED->ASSIGNED path with a
        // real status change, and the non-terminal agent-swap path with
        // fromStatus == toStatus == currentStatus (the status genuinely
        // didn't change, but the agent handoff must still leave a record —
        // otherwise moving a shipment off agent A silently loses the trail
        // of who used to own it).
        ShipmentEvent event = wasFailed
                ? ShipmentEvent.builder()
                        .shipment(shipment)
                        .fromStatus(ShipmentStatus.FAILED)
                        .toStatus(ShipmentStatus.ASSIGNED)
                        .changedBy(userRepository.getReferenceById(actorUserId))
                        .notes(req.notes() != null ? req.notes() : "Reassigned to agent: " + newAgent.getFullName())
                        .build()
                : ShipmentEvent.builder()
                        .shipment(shipment)
                        .fromStatus(currentStatus)
                        .toStatus(currentStatus)
                        .changedBy(userRepository.getReferenceById(actorUserId))
                        .notes(req.notes() != null ? req.notes() : "Reassigned to agent: " + newAgent.getFullName())
                        .build();
        shipmentEventRepository.save(event);

        Shipment loaded = shipmentRepository.findWithDetailById(shipment.getId())
                .orElse(shipment);
        return ShipmentResponseDto.from(loaded);
    }

    /**
     * List shipments assigned to the current agent.
     */
    @Transactional(readOnly = true)
    public Page<ShipmentSummaryDto> myAssignments(Long agentUserId, ShipmentStatus status, Pageable pageable) {
        Page<Shipment> page;
        if (status != null) {
            page = shipmentRepository.findByAssignedAgent_IdAndStatusIn(
                    agentUserId, EnumSet.of(status), pageable);
        } else {
            page = shipmentRepository.findByAssignedAgent_IdAndStatusIn(
                    agentUserId, NON_TERMINAL, pageable);
        }
        return page.map(ShipmentSummaryDto::from);
    }

    /**
     * Public tracking lookup by opaque token.
     */
    @Transactional(readOnly = true)
    public PublicTrackingResponse trackPublic(String token) {
        Shipment shipment = shipmentRepository.findByTrackingToken(token)
                .orElseThrow(() -> new ShipmentNotFoundException("Shipment not found for token: " + token));
        return PublicTrackingResponse.from(shipment);
    }

    /**
     * Filterable listing backing the admin register page. Reuses the same
     * {@link ShipmentSpecifications} builders as {@link #list}, extended
     * with a multi-status {@code statusIn} clause since the register page
     * sends its status filter as a comma-joined list rather than a single
     * value.
     */
    @Transactional(readOnly = true)
    public Page<Shipment> searchForRegister(Long businessId, List<ShipmentStatus> statuses,
                                             Long agentId, LocalDate from, LocalDate to,
                                             Pageable pageable) {
        Specification<Shipment> spec = Specification.where(ShipmentSpecifications.belongsToBusiness(businessId))
                .and(ShipmentSpecifications.fetchAssignedAgent());

        if (statuses != null && !statuses.isEmpty()) {
            spec = spec.and(ShipmentSpecifications.statusIn(statuses));
        }
        if (agentId != null) {
            spec = spec.and(ShipmentSpecifications.assignedToAgent(agentId));
        }
        if (from != null) {
            spec = spec.and(ShipmentSpecifications.createdAfter(from));
        }
        if (to != null) {
            spec = spec.and(ShipmentSpecifications.createdBefore(to));
        }

        return shipmentRepository.findAll(spec, pageable);
    }

    /**
     * Business-scoped lookup by tracking token for the admin register
     * inspect view. Mirrors the ownership-check posture of {@link #get}:
     * a token belonging to another business's shipment 404s rather than
     * leaking that the token exists.
     */
    @Transactional(readOnly = true)
    public Shipment getByTokenInBusiness(Long businessId, String token) {
        Shipment shipment = shipmentRepository.findWithDetailByTrackingToken(token)
                .orElseThrow(() -> new ShipmentNotFoundException("Shipment not found for token: " + token));
        if (!shipment.getBusiness().getId().equals(businessId)) {
            throw new ShipmentNotFoundException("Shipment not found for token: " + token);
        }
        return shipment;
    }
}
