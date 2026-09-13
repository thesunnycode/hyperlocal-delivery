package com.hyperlocal.delivery;

import java.util.UUID;

import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.Shipment;
import com.hyperlocal.delivery.model.ShipmentStatus;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.model.UserRole;

/**
 * Utility class with static/fluent builders for test data.
 * Provides pre-configured entity instances for integration tests.
 */
public final class TestDataFactory {

    private static final String DUMMY_BCRYPT_HASH =
            "$2a$12$dummyhashForTestingPurposesOnly00000000000000000000";

    private TestDataFactory() {
        // utility class
    }

    public static Business createBusiness(String name, String email) {
        return Business.builder()
                .name(name)
                .email(email)
                .passwordHash(DUMMY_BCRYPT_HASH)
                .build();
    }

    public static User createOwner(Business business, String email) {
        return User.builder()
                .business(business)
                .email(email)
                .passwordHash(DUMMY_BCRYPT_HASH)
                .role(UserRole.BUSINESS_OWNER)
                .fullName("Test Owner")
                .phone("+91-9876543210")
                .build();
    }

    public static User createAgent(Business business, String email) {
        return User.builder()
                .business(business)
                .email(email)
                .passwordHash(DUMMY_BCRYPT_HASH)
                .role(UserRole.DELIVERY_AGENT)
                .fullName("Test Agent")
                .phone("+91-9123456789")
                .build();
    }

    public static Shipment createShipment(Business business, User agent) {
        return Shipment.builder()
                .trackingToken(UUID.randomUUID().toString())
                .business(business)
                .assignedAgent(agent)
                .status(ShipmentStatus.ASSIGNED)
                .customerName("Test Customer")
                .customerPhone("+91-9000000000")
                .deliveryAddress("123 Test Street, Test City")
                .build();
    }
}
