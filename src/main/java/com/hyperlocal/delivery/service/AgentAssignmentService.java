package com.hyperlocal.delivery.service;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hyperlocal.delivery.exception.NoAgentsAvailableException;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.model.ShipmentStatusSets;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Handles auto-assignment of shipments to the least-loaded active delivery
 * agent within a business. Uses a pessimistic row lock to prevent concurrent
 * double-booking.
 */
@Service
@RequiredArgsConstructor
public class AgentAssignmentService {

    private static final Set<ShipmentStatus> ACTIVE = ShipmentStatusSets.ACTIVE_FOR_LOAD_COUNTING;

    private final UserRepository userRepository;

    /**
     * Pick the least-loaded active agent for the given business and acquire
     * a pessimistic write lock on the agent row.
     *
     * @param businessId the tenant to search within
     * @return the locked agent entity
     * @throws NoAgentsAvailableException if no eligible agents exist
     */
    @Transactional
    public User pickAndLockLeastLoadedAgent(Long businessId) {
        User candidate = userRepository
                .findAgentsByLoadAsc(businessId, ACTIVE, PageRequest.of(0, 1))
                .stream().findFirst()
                .orElseThrow(NoAgentsAvailableException::new);
        return userRepository.lockById(candidate.getId())
                .orElseThrow(NoAgentsAvailableException::new);
    }
}
