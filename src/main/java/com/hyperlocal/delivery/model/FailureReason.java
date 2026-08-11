package com.hyperlocal.delivery.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Reason recorded on a {@link DeliveryAttempt} when an attempt does not
 * result in successful delivery.
 *
 * <p>Persisted as a MySQL {@code ENUM} column and mapped via
 * {@code @Enumerated(EnumType.STRING)}.
 *
 * <p>Serialized to/from JSON using the frontend's human-readable labels
 * (e.g. {@code "Address not found"}) rather than the raw enum constant
 * name, via {@link JsonValue}/{@link JsonCreator}.
 */
public enum FailureReason {
    CUSTOMER_ABSENT("Customer absent"),
    ADDRESS_NOT_FOUND("Address not found"),
    REFUSED("Refused"),
    DAMAGED("Damaged"),
    OTHER("Other");

    private final String label;

    FailureReason(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static FailureReason fromLabel(String value) {
        for (FailureReason reason : values()) {
            if (reason.label.equalsIgnoreCase(value) || reason.name().equalsIgnoreCase(value)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Unknown failure reason: " + value);
    }
}
