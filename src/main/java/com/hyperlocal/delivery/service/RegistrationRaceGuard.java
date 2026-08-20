package com.hyperlocal.delivery.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.PendingRegistration;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.model.UserRole;
import com.hyperlocal.delivery.repository.BusinessRepository;
import com.hyperlocal.delivery.repository.PendingRegistrationRepository;
import com.hyperlocal.delivery.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Isolates the two inserts in the registration flow that can race a
 * concurrent identical request — the pending-registration upsert in
 * {@code initiateRegistration}, and the Business+User creation in
 * {@code createAccount} — into their own transactions.
 *
 * <p>Both inserts sit behind a check-then-write: another request for the
 * exact same email can slip in between the check and the write, and the
 * DB's unique constraint on {@code email} is what actually stops the
 * duplicate. When that happens, the loser's insert throws a
 * {@link org.springframework.dao.DataIntegrityViolationException}. If that
 * insert ran in the same transaction as the rest of the request, Hibernate
 * marks the whole transaction rollback-only the moment the flush fails —
 * catching the exception in application code does not undo that, so the
 * request still fails, just with a confusing
 * {@link org.springframework.transaction.UnexpectedRollbackException} at
 * commit instead of the original error. Running each insert here, in its
 * own {@code REQUIRES_NEW} transaction, means a failed insert only rolls
 * back this isolated transaction; {@link AuthService} can then catch the
 * exception cleanly and answer the race with a normal response.
 */
@Service
@RequiredArgsConstructor
class RegistrationRaceGuard {

    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void savePendingRegistration(PendingRegistration pending) {
        pendingRegistrationRepository.save(pending);
    }

    /**
     * Create the Business + User rows from an already-hashed password.
     * Mirrors what {@code AuthService.createAccount} used to do inline;
     * moved here only so the insert can be isolated in its own transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User createBusinessAndUser(String businessName, String ownerName, String email,
                                       String phone, String passwordHash) {
        // passwordHash on Business is a legacy NOT NULL column never
        // consulted for authentication (only User.passwordHash is) — store
        // a non-authenticable placeholder to satisfy the constraint.
        Business business = Business.builder()
                .name(businessName)
                .email(email)
                .passwordHash("{noop}NOT_USED_FOR_AUTH")
                .build();
        business = businessRepository.save(business);

        User user = User.builder()
                .business(business)
                .email(email)
                .passwordHash(passwordHash)
                .role(UserRole.BUSINESS_OWNER)
                .fullName(ownerName)
                .phone(phone)
                .build();
        return userRepository.save(user);
    }
}
