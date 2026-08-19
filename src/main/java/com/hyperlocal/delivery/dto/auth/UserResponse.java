package com.hyperlocal.delivery.dto.auth;

import com.hyperlocal.delivery.model.User;

/**
 * Public-facing representation of a user profile. Includes the user's
 * business context and role.
 */
public record UserResponse(
        Long id,
        String email,
        String name,
        String phone,
        String role,
        Long businessId,
        String businessName,
        String businessPhone,
        Boolean isActive,
        String createdAt
) {

    /**
     * Map a {@link User} entity to its public DTO representation.
     */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole().getShortCode(),
                user.getBusiness().getId(),
                user.getBusiness().getName(),
                user.getBusiness().getPhone(),
                user.getIsActive(),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
        );
    }
}
