package com.hyperlocal.delivery.dto.auth;

/**
 * What is actually parked in {@code pending_registrations} between the two
 * halves of the OTP registration flow.
 *
 * <p>This type exists so the raw password never reaches the database. The
 * password is BCrypt-hashed in {@code AuthService.initiateRegistration} and
 * only the hash travels through this record, so a dump of
 * {@code pending_registrations.payload_json} yields nothing an attacker can
 * sign in with. Serializing {@link RegisterRequest} directly — which is what
 * this replaced — wrote the plaintext password to a {@code TEXT} column.
 *
 * @param passwordHash BCrypt hash, already encoded. Never the raw password.
 */
public record PendingRegistrationPayload(
        String businessName,
        String ownerName,
        String email,
        String phone,
        String passwordHash
) {
}
