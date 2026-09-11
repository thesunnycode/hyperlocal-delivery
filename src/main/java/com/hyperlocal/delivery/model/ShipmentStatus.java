package com.hyperlocal.delivery.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lifecycle state of a {@link Shipment}.
 *
 * <p>The declaration order matches the canonical forward progression of the
 * shipment state machine: {@code ASSIGNED → PICKED_UP → IN_TRANSIT →
 * OUT_FOR_DELIVERY → DELIVERED}, with {@code FAILED} and {@code RETURNED}
 * as terminal alternative outcomes, and {@code CANCELLED} as a terminal
 * outcome the owner can trigger directly from any non-terminal status. A
 * shipment is auto-assigned and starts life directly in {@code ASSIGNED} —
 * there is no {@code CREATED} state. Persisted as a MySQL {@code ENUM}
 * column and mapped via {@code @Enumerated(EnumType.STRING)}.
 *
 * <p>Serializes to/from JSON as lowercase snake_case ({@code assigned},
 * {@code picked_up}, etc.) via {@link #getWireValue()} /
 * {@link #fromWireValue(String)}, while {@link #name()} (used for JPA
 * persistence) remains the upper-snake-case enum constant name.
 */
public enum ShipmentStatus {
    ASSIGNED("assigned"),
    PICKED_UP("picked_up"),
    IN_TRANSIT("in_transit"),
    OUT_FOR_DELIVERY("out_for_delivery"),
    DELIVERED("delivered"),
    FAILED("failed"),
    RETURNED("returned"),
    CANCELLED("cancelled");

    private final String wireValue;

    ShipmentStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String getWireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ShipmentStatus fromWireValue(String value) {
        for (ShipmentStatus status : values()) {
            if (status.wireValue.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown shipment status: " + value);
    }
}
