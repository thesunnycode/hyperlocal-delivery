package com.hyperlocal.delivery.security;

import com.hyperlocal.delivery.model.UserRole;

/**
 * Lightweight principal carried in JWT claims and used by services and
 * controllers to identify the caller without pulling the full
 * {@code User} entity.
 *
 * @param userId     primary key of the authenticated user
 * @param email      login email (also serialised as the JWT {@code sub})
 * @param businessId tenant this user belongs to
 * @param role       role of the user ({@code BUSINESS_OWNER} /
 *                   {@code DELIVERY_AGENT})
 */
public record AuthenticatedPrincipal(
        Long userId,
        String email,
        Long businessId,
        UserRole role) {
}
