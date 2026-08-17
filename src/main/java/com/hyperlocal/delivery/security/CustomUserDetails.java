package com.hyperlocal.delivery.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.model.UserRole;

/**
 * Spring Security {@link UserDetails} adapter around our {@link User}
 * entity. Exposes a single authority derived from {@link UserRole}
 * ({@code ROLE_BUSINESS_OWNER} or {@code ROLE_DELIVERY_AGENT}).
 *
 * <p>Account validity reflects the entity: a user is neither enabled
 * nor unlocked when they are inactive or soft-deleted.
 */
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String email;
    private final String passwordHash;
    private final Long businessId;
    private final UserRole role;
    private final boolean active;
    private final boolean deleted;

    public CustomUserDetails(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.businessId = user.getBusiness() != null ? user.getBusiness().getId() : null;
        this.role = user.getRole();
        this.active = Boolean.TRUE.equals(user.getIsActive());
        this.deleted = user.getDeletedAt() != null;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public UserRole getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return !deleted;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active && !deleted;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active && !deleted;
    }
}
