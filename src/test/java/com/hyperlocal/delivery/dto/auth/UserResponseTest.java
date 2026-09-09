package com.hyperlocal.delivery.dto.auth;

import com.hyperlocal.delivery.TestDataFactory;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link UserResponse#from(User)} actually emits the
 * frontend-facing role short code ("OWNER"/"AGENT") rather than the raw
 * enum name. This is a DTO-level regression test: {@link UserResponse#role()}
 * is a plain {@code String} field populated eagerly in {@code from()},
 * so Jackson's {@code @JsonValue} on {@code UserRole} never gets a chance
 * to run for this field — the mapping expression itself must produce the
 * short code.
 */
class UserResponseTest {

    @Test
    void fromMapsBusinessOwnerRoleToOwnerShortCode() {
        Business business = TestDataFactory.createBusiness("Test Biz", "biz@example.com");
        User owner = TestDataFactory.createOwner(business, "owner@example.com");

        UserResponse response = UserResponse.from(owner);

        assertThat(response.role()).isEqualTo("OWNER");
    }

    @Test
    void fromMapsDeliveryAgentRoleToAgentShortCode() {
        Business business = TestDataFactory.createBusiness("Test Biz", "biz@example.com");
        User agent = TestDataFactory.createAgent(business, "agent@example.com");

        UserResponse response = UserResponse.from(agent);

        assertThat(response.role()).isEqualTo("AGENT");
    }

    @Test
    void fromExposesUsersFullNameAsTheNameField() {
        Business business = TestDataFactory.createBusiness("Test Biz", "biz@example.com");
        User owner = TestDataFactory.createOwner(business, "owner@example.com");

        UserResponse response = UserResponse.from(owner);

        assertThat(response.name()).isEqualTo(owner.getFullName());
    }
}
