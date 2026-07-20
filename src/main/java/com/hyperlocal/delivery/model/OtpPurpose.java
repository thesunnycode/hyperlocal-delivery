package com.hyperlocal.delivery.model;

/**
 * The reason an OTP code was generated.
 *
 * <p>Persisted as a MySQL {@code ENUM} column and mapped via
 * {@code @Enumerated(EnumType.STRING)}.
 */
public enum OtpPurpose {
    REGISTRATION,
    PASSWORD_RESET
}
