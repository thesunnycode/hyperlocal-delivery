package com.hyperlocal.delivery.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.hyperlocal.delivery.config.MailProperties;
import com.hyperlocal.delivery.dto.auth.AuthResponse;
import com.hyperlocal.delivery.dto.auth.LoginRequest;
import com.hyperlocal.delivery.dto.auth.OtpSentResponse;
import com.hyperlocal.delivery.dto.auth.PendingRegistrationPayload;
import com.hyperlocal.delivery.dto.auth.RegisterRequest;
import com.hyperlocal.delivery.dto.auth.ResetTokenResponse;
import com.hyperlocal.delivery.dto.auth.TokenRefreshResponse;
import com.hyperlocal.delivery.dto.auth.UpdateAccountRequest;
import com.hyperlocal.delivery.dto.auth.UserResponse;
import com.hyperlocal.delivery.exception.DuplicateEmailException;
import com.hyperlocal.delivery.exception.ErrorCode;
import com.hyperlocal.delivery.exception.InvalidCredentialsException;
import com.hyperlocal.delivery.exception.InvalidRefreshTokenException;
import com.hyperlocal.delivery.exception.InvalidResetTokenException;
import com.hyperlocal.delivery.exception.MailDeliveryException;
import com.hyperlocal.delivery.exception.OtpVerificationException;
import com.hyperlocal.delivery.exception.ValidationException;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.OtpPurpose;
import com.hyperlocal.delivery.model.PendingRegistration;
import com.hyperlocal.delivery.model.RefreshToken;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.model.UserRole;
import com.hyperlocal.delivery.repository.BusinessRepository;
import com.hyperlocal.delivery.repository.PendingRegistrationRepository;
import com.hyperlocal.delivery.repository.RefreshTokenRepository;
import com.hyperlocal.delivery.repository.UserRepository;
import com.hyperlocal.delivery.security.AuthenticatedPrincipal;
import com.hyperlocal.delivery.security.JwtUtil;

import lombok.RequiredArgsConstructor;

/**
 * Handles registration, login, token refresh, logout, and profile
 * retrieval for the authentication flow.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Pending registration payloads expire after 10 minutes. */
    private static final int PENDING_REGISTRATION_TTL_MINUTES = 10;

    /**
     * Canonical form of an email address for storage and lookup.
     *
     * <p>Every read and every write goes through this. Storing one casing and
     * querying another is how you end up with two accounts for the same person,
     * or an account that cannot log in with the address it was created under.
     */
    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final PasswordResetMailer mailer;
    private final MailProperties mailProperties;
    private final OtpService otpService;
    private final OtpEmailService otpEmailService;
    private final ObjectMapper objectMapper;
    private final LoginRateLimiter loginRateLimiter;
    private final RegistrationRaceGuard registrationRaceGuard;

    /**
     * Initiate the OTP-based registration flow. Validates the request,
     * checks for duplicate email, stores the registration payload in
     * {@code pending_registrations}, generates an OTP, sends it via email,
     * and returns a success response with the masked email.
     *
     * <p>The actual Business + User creation is deferred to the
     * verify-registration-otp endpoint (after successful OTP validation).
     */
    @Transactional
    public OtpSentResponse initiateRegistration(RegisterRequest req) {
        String normalizedEmail = normalize(req.email());

        // An address that already has an account is rejected outright, rather
        // than being given the same "OTP sent" response as a fresh address:
        // a clear "already registered, log in instead" is far less confusing
        // to a real user than silently no-op'ing behind an identical success
        // screen. This trades away enumeration-resistance (an attacker can
        // now probe which emails have accounts) for a normal registration
        // UX; /forgot-password and /resend-otp still use the neutral,
        // non-disclosing response, since a password-reset flow is a much
        // more attractive enumeration target than registration.
        //
        // Checked against the normalized address: "New@Test.com" and
        // "new@test.com" are the same account, and matching only the raw string
        // would let the second one through as a duplicate registration.
        if (businessRepository.existsByEmail(normalizedEmail)
                || userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        // Hash before anything is persisted. The pending_registrations row is a
        // plain TEXT column that survives until the hourly cleanup job runs, so
        // the raw password must never be written into it.
        PendingRegistrationPayload payload = new PendingRegistrationPayload(
                req.businessName(),
                req.ownerName(),
                normalizedEmail,
                req.phone(),
                passwordEncoder.encode(req.password()));

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize registration payload", e);
        }

        // Upsert: if a pending registration already exists for this email, overwrite it
        PendingRegistration pending = pendingRegistrationRepository.findByEmail(normalizedEmail)
                .orElse(PendingRegistration.builder().email(normalizedEmail).build());
        pending.setPayloadJson(payloadJson);
        pending.setExpiresAt(LocalDateTime.now().plusMinutes(PENDING_REGISTRATION_TTL_MINUTES));
        try {
            registrationRaceGuard.savePendingRegistration(pending);
        } catch (DataIntegrityViolationException e) {
            // The findByEmail lookup above raced with another /register call for
            // this exact email: both saw no existing row and both tried to insert.
            // uq_pending_reg_email let only one through. That other request is
            // already sending its own OTP, so answer with the same neutral
            // response instead of leaking the race as a 500.
            log.info("Registration race lost for a pending registration; a concurrent "
                    + "request already owns this email's pending row");
            return new OtpSentResponse(
                    "Verification code sent to your email",
                    maskEmail(normalizedEmail));
        }

        // Generate OTP and send via email
        String otpCode = otpService.generateOtp(normalizedEmail, OtpPurpose.REGISTRATION);
        otpEmailService.sendOtpEmail(normalizedEmail, otpCode, OtpPurpose.REGISTRATION);

        return new OtpSentResponse(
                "Verification code sent to your email",
                maskEmail(normalizedEmail)
        );
    }

    /**
     * Complete the OTP-based registration flow. Validates the submitted OTP
     * code, retrieves the pending registration payload, and — on success —
     * creates the Business + User entities and issues JWT tokens.
     *
     * @throws OtpVerificationException with the appropriate error code on
     *         OTP validation failure (INVALID_OTP, OTP_EXPIRED, MAX_ATTEMPTS_EXCEEDED,
     *         NO_PENDING_VERIFICATION)
     * @throws OtpVerificationException with SESSION_EXPIRED if the pending
     *         registration payload has expired or doesn't exist
     */
    @Transactional
    public AuthResponse verifyRegistrationOtp(String email, String code) {
        String normalizedEmail = normalize(email);

        // Validate OTP
        OtpValidationResult result = otpService.validateOtp(normalizedEmail, code, OtpPurpose.REGISTRATION);

        if (result instanceof OtpValidationResult.Failure failure) {
            throw mapOtpFailure(failure);
        }

        // OTP is valid — retrieve pending registration payload
        PendingRegistration pending = pendingRegistrationRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new OtpVerificationException(
                        ErrorCode.SESSION_EXPIRED,
                        "Registration session has expired. Please restart registration."));

        // Check if payload has expired
        if (LocalDateTime.now().isAfter(pending.getExpiresAt())) {
            pendingRegistrationRepository.delete(pending);
            throw new OtpVerificationException(
                    ErrorCode.SESSION_EXPIRED,
                    "Registration session has expired. Please restart registration.");
        }

        // Deserialize registration payload. A row written by an older build
        // stored a "password" field and no "passwordHash", so it cannot produce
        // a usable account — treat it, and any other malformed row, as an
        // expired session rather than a 500.
        PendingRegistrationPayload payload;
        try {
            payload = objectMapper.readValue(pending.getPayloadJson(), PendingRegistrationPayload.class);
        } catch (JacksonException e) {
            pendingRegistrationRepository.delete(pending);
            throw new OtpVerificationException(
                    ErrorCode.SESSION_EXPIRED,
                    "Registration session has expired. Please restart registration.");
        }
        if (payload.passwordHash() == null || payload.passwordHash().isBlank()) {
            pendingRegistrationRepository.delete(pending);
            throw new OtpVerificationException(
                    ErrorCode.SESSION_EXPIRED,
                    "Registration session has expired. Please restart registration.");
        }

        // Create Business + User + tokens from the already-hashed payload
        AuthResponse response = createAccount(
                payload.businessName(), payload.ownerName(), payload.email(),
                payload.phone(), payload.passwordHash());

        // Clean up the pending registration
        pendingRegistrationRepository.delete(pending);

        return response;
    }

    /**
     * Create the Business + User rows from an <em>already hashed</em> password
     * and issue tokens.
     *
     * <p>Takes a hash rather than a {@code RegisterRequest} so the OTP flow can
     * hand over the hash it computed back in
     * {@link #initiateRegistration}. That is what lets the pending payload hold
     * a hash instead of the raw password.
     *
     * <p>{@code email} must already be normalized — {@link #verifyRegistrationOtp}
     * reads it from a payload written by {@code initiateRegistration}.
     *
     * <p>Not {@code @Transactional} itself — Spring's proxying would ignore the
     * annotation on a private method anyway, and the caller already carries it.
     */
    private AuthResponse createAccount(String businessName, String ownerName, String email,
                                       String phone, String passwordHash) {
        // Defensive recheck: another registration may have completed between
        // the OTP being sent and this verification.
        if (businessRepository.existsByEmail(email) || userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        User user;
        try {
            user = registrationRaceGuard.createBusinessAndUser(businessName, ownerName, email, phone, passwordHash);
        } catch (DataIntegrityViolationException e) {
            // Another concurrent verify-registration-otp call for this exact
            // email won the race between the recheck above and the insert
            // (e.g. the same code submitted twice at once). The DB's unique
            // constraint is the real guarantee; this just translates its
            // rejection into the same clean error the recheck already throws
            // instead of a raw 500.
            throw new DuplicateEmailException(email);
        }

        return issueTokens(user);
    }

    /**
     * Authenticate a user by email and password. Returns tokens on success.
     * Rate-limited: rejects after 10 failed attempts per email per 15 minutes.
     */
    @Transactional
    public AuthResponse login(LoginRequest req) {
        // Accounts are stored under the normalized address, so look up and
        // rate-limit under it too — otherwise signing in with the same casing
        // you registered with can miss the row entirely.
        String normalizedEmail = normalize(req.email());
        loginRateLimiter.checkAllowed(normalizedEmail);

        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> {
                    loginRateLimiter.recordFailedAttempt(normalizedEmail);
                    return new InvalidCredentialsException();
                });

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            loginRateLimiter.recordFailedAttempt(req.email());
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            loginRateLimiter.recordFailedAttempt(req.email());
            throw new InvalidCredentialsException();
        }

        loginRateLimiter.recordSuccess(req.email());
        return issueTokens(user);
    }

    /**
     * Exchange a valid refresh token for a new access/refresh pair (rotation).
     * The old refresh token is consumed and cannot be reused.
     */
    @Transactional
    public TokenRefreshResponse refresh(String rawRefreshToken) {
        String hash = JwtUtil.sha256Hex(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.deleteByTokenHash(hash);
            throw new InvalidRefreshTokenException();
        }

        User user = stored.getUser();
        if (!Boolean.TRUE.equals(user.getIsActive()) || user.getDeletedAt() != null) {
            throw new InvalidRefreshTokenException();
        }

        // Consume the token (rotation) via a bulk delete-by-hash rather than
        // an entity delete. A bulk delete returns how many rows it actually
        // removed instead of assuming one is there to remove — so a
        // concurrent request that redeemed this exact token a moment earlier
        // (between our findByTokenHash above and this statement) is caught
        // here as a plain "0 rows affected", not an
        // ObjectOptimisticLockingFailureException thrown at commit time from
        // deleting a row that's already gone.
        long deleted = refreshTokenRepository.deleteByTokenHash(hash);
        if (deleted == 0) {
            throw new InvalidRefreshTokenException();
        }

        // Issue new pair
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                user.getId(), user.getEmail(), user.getBusiness().getId(), user.getRole());
        String newAccessToken = jwtUtil.signAccess(principal);
        String newRawRefresh = jwtUtil.generateOpaqueToken();

        RefreshToken newToken = RefreshToken.builder()
                .user(user)
                .tokenHash(JwtUtil.sha256Hex(newRawRefresh))
                .expiresAt(LocalDateTime.now().plusDays(jwtUtil.refreshTtl().toDays()))
                .build();
        refreshTokenRepository.save(newToken);

        return new TokenRefreshResponse(
                newAccessToken,
                newRawRefresh,
                jwtUtil.accessTtl().toSeconds()
        );
    }

    /**
     * Revoke all refresh tokens for the given user (logout-all).
     */
    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUser_Id(userId);
    }

    /**
     * Begin the OTP-based password-reset flow. Always returns without
     * throwing, whether or not the email belongs to an account — this never
     * reveals account existence to the caller (non-disclosure).
     *
     * <p>If the email is registered: generates an OTP via {@link OtpService},
     * sends it via {@link OtpEmailService}. If email delivery fails, the
     * error is logged silently — surfacing it would disclose that the account
     * exists.
     *
     * <p>If the email is NOT registered: does nothing, returns silently with
     * identical timing characteristics.
     */
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmailAndDeletedAtIsNull(normalize(email)).ifPresent(user -> {
            try {
                String code = otpService.generateOtp(user.getEmail(), OtpPurpose.PASSWORD_RESET);
                otpEmailService.sendOtpEmail(user.getEmail(), code, OtpPurpose.PASSWORD_RESET);
            } catch (MailDeliveryException ex) {
                log.error("Failed to send password-reset OTP email for user {}", user.getId(), ex);
            } catch (Exception ex) {
                // Catch any other exception (e.g., rate limiting) silently to
                // maintain non-disclosure — the response must be identical
                // regardless of email existence.
                log.error("Password-reset OTP flow failed for user {}", user.getId(), ex);
            }
        });
    }

    /**
     * Verify a password-reset OTP and issue a short-lived reset session
     * token (JWT, 5-minute TTL) containing the email claim. The token
     * authorizes setting a new password via the reset-password endpoint.
     *
     * @param email the email address associated with the reset request
     * @param code  the 6-digit OTP code submitted by the user
     * @return a {@link ResetTokenResponse} containing the JWT reset session token
     * @throws OtpVerificationException if OTP validation fails
     */
    @Transactional
    public ResetTokenResponse verifyResetOtp(String email, String code) {
        OtpValidationResult result = otpService.validateOtp(email, code, OtpPurpose.PASSWORD_RESET);

        if (result instanceof OtpValidationResult.Success) {
            String resetToken = jwtUtil.signResetToken(normalize(email));
            return new ResetTokenResponse(resetToken);
        }

        // Map failure reason to the appropriate exception
        var failure = (OtpValidationResult.Failure) result;
        throw switch (failure.reason()) {
            case INVALID_CODE -> new OtpVerificationException(
                    ErrorCode.INVALID_OTP, "Invalid verification code", failure.attemptsRemaining());
            case EXPIRED -> new OtpVerificationException(
                    ErrorCode.OTP_EXPIRED, "Verification code has expired");
            case MAX_ATTEMPTS -> new OtpVerificationException(
                    ErrorCode.MAX_ATTEMPTS_EXCEEDED, "Maximum verification attempts exceeded");
            case NO_RECORD -> new OtpVerificationException(
                    ErrorCode.NO_PENDING_VERIFICATION, "No pending verification for this email");
        };
    }

    /**
     * Redeem a JWT password-reset session token: verify signature and expiry,
     * extract the email, validate the new password, update the password hash,
     * and revoke every existing refresh token for the account (force re-login
     * everywhere).
     *
     * <p>Password validation is checked <em>after</em> token validation but
     * does not consume the token — the JWT remains valid for retry until its
     * 5-minute TTL expires.
     */
    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        // 1. Parse and validate the JWT reset token (signature, expiry, purpose)
        String email;
        java.time.Instant tokenIssuedAt;
        try {
            io.jsonwebtoken.Claims claims = jwtUtil.parseResetTokenClaims(resetToken);
            email = claims.getSubject();
            tokenIssuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : null;
        } catch (Exception ex) {
            throw new InvalidResetTokenException();
        }

        // 2. Validate new password (≥8 chars). Token is NOT consumed on failure.
        if (newPassword == null || newPassword.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters");
        }

        // 3. Look up user by email
        User user = userRepository.findByEmailAndDeletedAtIsNull(normalize(email))
                .orElseThrow(InvalidResetTokenException::new);

        // 4. Replay protection: reject if a password reset already occurred
        //    after this token was issued. Uses the dedicated passwordChangedAt
        //    column (not updatedAt) so profile edits don't invalidate live
        //    reset links, and truncates both sides to seconds to avoid
        //    sub-second rounding issues between JWT iat and DB timestamps.
        if (tokenIssuedAt != null && user.getPasswordChangedAt() != null) {
            java.time.Instant lastReset = user.getPasswordChangedAt()
                    .atZone(java.time.ZoneId.systemDefault()).toInstant()
                    .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            java.time.Instant tokenIat = tokenIssuedAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            if (!lastReset.isBefore(tokenIat)) {
                throw new InvalidResetTokenException();
            }
        }

        // 5. Update password hash and record the reset timestamp
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        // 6. Revoke all refresh tokens for this user
        refreshTokenRepository.deleteByUser_Id(user.getId());
    }

    /**
     * Retrieve the profile of the currently authenticated user.
     */
    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
        return UserResponse.from(user);
    }

    /**
     * Update the caller's own editable profile fields (X3): full name,
     * phone, and — for a {@code BUSINESS_OWNER} only — the business name.
     * Email and role are never editable through this endpoint —
     * {@link UpdateAccountRequest} has no fields for them.
     */
    @Transactional
    public UserResponse updateAccount(Long userId, UpdateAccountRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        if (req.fullName() != null) {
            user.setFullName(req.fullName());
        }
        if (req.phone() != null) {
            user.setPhone(req.phone());
        }
        // Business name is owner-only (X3): a delivery agent sending this
        // field has it silently ignored, same posture as email/role.
        if (req.businessName() != null && user.getRole() == UserRole.BUSINESS_OWNER) {
            Business business = user.getBusiness();
            business.setName(req.businessName());
            businessRepository.save(business);
        }
        if (req.businessPhone() != null && user.getRole() == UserRole.BUSINESS_OWNER) {
            Business business = user.getBusiness();
            business.setPhone(req.businessPhone());
            businessRepository.save(business);
        }
        user = userRepository.save(user);

        return UserResponse.from(user);
    }

    private AuthResponse issueTokens(User user) {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                user.getId(), user.getEmail(), user.getBusiness().getId(), user.getRole());
        String accessToken = jwtUtil.signAccess(principal);
        String rawRefresh = jwtUtil.generateOpaqueToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(JwtUtil.sha256Hex(rawRefresh))
                .expiresAt(LocalDateTime.now().plusDays(jwtUtil.refreshTtl().toDays()))
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.of(
                accessToken,
                rawRefresh,
                jwtUtil.accessTtl().toSeconds(),
                UserResponse.from(user)
        );
    }

    /**
     * Mask an email address for display purposes. Shows the first two
     * characters of the local part, masks the rest with asterisks, and
     * keeps the full domain. For example: {@code "owner@test.com"} becomes
     * {@code "ow***@test.com"}.
     */
    static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.substring(0, 2) + "***" + domain;
    }

    /**
     * Map an OTP validation failure to the appropriate domain exception.
     */
    private OtpVerificationException mapOtpFailure(OtpValidationResult.Failure failure) {
        return switch (failure.reason()) {
            case INVALID_CODE -> new OtpVerificationException(
                    ErrorCode.INVALID_OTP,
                    "The verification code is incorrect",
                    failure.attemptsRemaining());
            case EXPIRED -> new OtpVerificationException(
                    ErrorCode.OTP_EXPIRED,
                    "The verification code has expired. Please request a new one.");
            case MAX_ATTEMPTS -> new OtpVerificationException(
                    ErrorCode.MAX_ATTEMPTS_EXCEEDED,
                    "Maximum verification attempts exceeded. Please request a new code.");
            case NO_RECORD -> new OtpVerificationException(
                    ErrorCode.NO_PENDING_VERIFICATION,
                    "No pending verification found for this email address.");
        };
    }
}
