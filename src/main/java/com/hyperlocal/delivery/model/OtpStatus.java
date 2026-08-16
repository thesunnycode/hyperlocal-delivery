package com.hyperlocal.delivery.model;

/**
 * Lifecycle state of an {@link OtpRecord}.
 *
 * <ul>
 *   <li>{@code ACTIVE} — code is pending verification</li>
 *   <li>{@code VERIFIED} — code was successfully validated</li>
 *   <li>{@code INVALIDATED} — code was superseded or max attempts reached</li>
 * </ul>
 *
 * <p>Persisted as a MySQL {@code ENUM} column and mapped via
 * {@code @Enumerated(EnumType.STRING)}.
 */
public enum OtpStatus {
    ACTIVE,
    VERIFIED,
    INVALIDATED
}
