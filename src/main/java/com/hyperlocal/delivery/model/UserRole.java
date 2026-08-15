package com.hyperlocal.delivery.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Role assigned to a {@link User} within a business tenant.
 *
 * <p>Persisted as {@code ENUM('BUSINESS_OWNER','DELIVERY_AGENT')} in MySQL
 * and mapped via {@code @Enumerated(EnumType.STRING)}; values are also used
 * as Spring Security authorities {@code ROLE_BUSINESS_OWNER} and
 * {@code ROLE_DELIVERY_AGENT}. Note that {@code .name()} and the authority
 * strings are unaffected by the {@link JsonValue} short code below — Jackson
 * serialization and Spring Security's authority derivation are independent
 * mechanisms.
 */
public enum UserRole {
    BUSINESS_OWNER("OWNER"),
    DELIVERY_AGENT("AGENT");

    private final String shortCode;

    UserRole(String shortCode) {
        this.shortCode = shortCode;
    }

    @JsonValue
    public String getShortCode() {
        return shortCode;
    }

    @JsonCreator
    public static UserRole fromShortCode(String value) {
        for (UserRole role : values()) {
            if (role.shortCode.equalsIgnoreCase(value) || role.name().equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}
