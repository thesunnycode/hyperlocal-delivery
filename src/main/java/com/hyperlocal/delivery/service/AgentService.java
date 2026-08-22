package com.hyperlocal.delivery.service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hyperlocal.delivery.dto.agent.AgentResponse;
import com.hyperlocal.delivery.dto.agent.AgentSummaryDto;
import com.hyperlocal.delivery.dto.agent.CreateAgentRequest;
import com.hyperlocal.delivery.dto.agent.UpdateAgentRequest;
import com.hyperlocal.delivery.exception.AgentHasActiveShipmentsException;
import com.hyperlocal.delivery.exception.AgentNotFoundException;
import com.hyperlocal.delivery.exception.DuplicateEmailException;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.model.ShipmentStatusSets;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.model.UserRole;
import com.hyperlocal.delivery.repository.BusinessRepository;
import com.hyperlocal.delivery.repository.RefreshTokenRepository;
import com.hyperlocal.delivery.repository.ShipmentRepository;
import com.hyperlocal.delivery.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Business logic for delivery agent CRUD operations. All methods are
 * tenant-scoped via {@code businessId}.
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private final UserRepository userRepository;
    private final ShipmentRepository shipmentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Set<ShipmentStatus> ACTIVE_STATUSES = ShipmentStatusSets.ACTIVE_FOR_LOAD_COUNTING;

    /**
     * Create a new delivery agent within the given business.
     *
     * <p>The owner-facing "Add agent" UI never collects a password (agents
     * are meant to be onboarded via invite/reset-link, not given an
     * owner-chosen password) so {@code req.password()} is commonly absent
     * or blank. In that case a cryptographically random password is
     * generated here and hashed through the same {@link PasswordEncoder}
     * as every other account; the agent has no way to know it and is
     * expected to reach a working login via the existing
     * forgot-password/reset-password flow, same as any other user. When a
     * caller does supply a password (e.g. a future admin-invite flow that
     * lets the owner choose one), that value is used instead.</p>
     */
    @Transactional
    public AgentResponse create(Long businessId, CreateAgentRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new DuplicateEmailException(req.email());
        }

        String rawPassword = (req.password() == null || req.password().isBlank())
                ? java.util.UUID.randomUUID().toString()
                : req.password();

        User agent = User.builder()
                .business(businessRepository.getReferenceById(businessId))
                .email(req.email())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(UserRole.DELIVERY_AGENT)
                .fullName(req.fullName())
                .phone(req.phone())
                .build();

        try {
            agent = userRepository.save(agent);
        } catch (DataIntegrityViolationException e) {
            // Another concurrent create (for this business or, since email is
            // globally unique, any business) won the race between the
            // existsByEmail check above and this insert. The DB's unique
            // constraint is the real guarantee; this just translates its
            // rejection into the same clean error the check already throws
            // instead of a raw 500.
            throw new DuplicateEmailException(req.email());
        }
        return AgentResponse.from(agent);
    }

    /**
     * Paginated listing of agents, optionally filtered by active status.
     *
     * <p>When {@code active} is {@code null} (the "All" tab, and the
     * default with no query param at all) every agent in the tenant is
     * returned regardless of active/deactivated state, via
     * {@link UserRepository#findByBusiness_IdAndRole}. Deactivating an
     * agent sets {@code deletedAt} as part of the soft delete (see
     * {@link #deactivate}), so filtering on {@code deletedAt IS NULL} here
     * — as an earlier version of this method did — silently dropped
     * deactivated agents from every list view with no UI path back to
     * them. Only when the caller explicitly passes {@code active=true} or
     * {@code active=false} is the result narrowed to that state.</p>
     */
    @Transactional(readOnly = true)
    public Page<AgentSummaryDto> list(Long businessId, Boolean active, Pageable pageable) {
        Page<User> page;
        if (active != null) {
            page = userRepository.findByBusiness_IdAndRoleAndIsActive(
                    businessId, UserRole.DELIVERY_AGENT, active, pageable);
        } else {
            page = userRepository.findByBusiness_IdAndRole(
                    businessId, UserRole.DELIVERY_AGENT, pageable);
        }
        return page.map(u -> AgentSummaryDto.from(u,
                shipmentRepository.countByAssignedAgent_IdAndStatusIn(u.getId(), ACTIVE_STATUSES)));
    }

    /**
     * Get a single agent with delivery statistics.
     *
     * <p>Uses {@link #findAgentIncludingDeactivated(Long, Long)} rather than
     * the active-only lookup: the owner-facing agent detail pane must be
     * able to show a deactivated agent (with {@code active: false} and a
     * "Reactivate" button) instead of 404ing, which is also why the agents
     * list supports an "Active"/"All" filter toggle in the first place.</p>
     */
    @Transactional(readOnly = true)
    public AgentResponse get(Long businessId, Long agentId) {
        User agent = findAgentIncludingDeactivated(businessId, agentId);
        long active = shipmentRepository.countByAssignedAgent_IdAndStatusIn(agentId, ACTIVE_STATUSES);
        long delivered = shipmentRepository.countByAssignedAgent_IdAndStatusIn(
                agentId, EnumSet.of(ShipmentStatus.DELIVERED));
        long failed = shipmentRepository.countByAssignedAgent_IdAndStatusIn(
                agentId, EnumSet.of(ShipmentStatus.FAILED));
        return AgentResponse.from(agent, active, delivered, failed);
    }

    /**
     * Update an agent's profile fields.
     */
    @Transactional
    public AgentResponse update(Long businessId, Long agentId, UpdateAgentRequest req) {
        User agent = findAgent(businessId, agentId);
        agent.setFullName(req.fullName());
        agent.setPhone(req.phone());
        agent = userRepository.save(agent);
        return AgentResponse.from(agent);
    }

    /**
     * Soft-delete (deactivate) an agent. Blocks if the agent has active
     * shipments.
     */
    @Transactional
    public void deactivate(Long businessId, Long agentId) {
        User agent = findAgent(businessId, agentId);
        long activeCount = shipmentRepository.countByAssignedAgent_IdAndStatusIn(agentId, ACTIVE_STATUSES);
        if (activeCount > 0) {
            throw new AgentHasActiveShipmentsException(activeCount);
        }
        agent.setIsActive(false);
        agent.setDeletedAt(LocalDateTime.now());
        userRepository.save(agent);
        refreshTokenRepository.deleteByUser_Id(agentId);
    }

    /**
     * Reactivate a previously deactivated agent, restoring both the active
     * flag and clearing the soft-delete marker so the agent is once again
     * visible to tenant-scoped lookups and eligible for auto-assignment
     * (which requires both {@code isActive = true} and
     * {@code deletedAt IS NULL} — see
     * {@link com.hyperlocal.delivery.repository.UserRepository#findAgentsByLoadAsc}).
     */
    @Transactional
    public AgentResponse reactivate(Long businessId, Long agentId) {
        User agent = findAgentIncludingDeactivated(businessId, agentId);
        agent.setIsActive(true);
        agent.setDeletedAt(null);
        agent = userRepository.save(agent);
        return AgentResponse.from(agent);
    }

    private User findAgent(Long businessId, Long agentId) {
        User agent = userRepository.findByIdAndBusiness_IdAndDeletedAtIsNull(agentId, businessId)
                .orElseThrow(() -> new AgentNotFoundException(agentId));
        if (agent.getRole() != UserRole.DELIVERY_AGENT) {
            throw new AgentNotFoundException(agentId);
        }
        return agent;
    }

    private User findAgentIncludingDeactivated(Long businessId, Long agentId) {
        User agent = userRepository.findByIdAndBusiness_Id(agentId, businessId)
                .orElseThrow(() -> new AgentNotFoundException(agentId));
        if (agent.getRole() != UserRole.DELIVERY_AGENT) {
            throw new AgentNotFoundException(agentId);
        }
        return agent;
    }
}
