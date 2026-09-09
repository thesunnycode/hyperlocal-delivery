package com.hyperlocal.delivery.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import tools.jackson.databind.ObjectMapper;
import com.hyperlocal.delivery.config.MailProperties;
import com.hyperlocal.delivery.dto.auth.AuthResponse;
import com.hyperlocal.delivery.dto.auth.LoginRequest;
import com.hyperlocal.delivery.dto.auth.OtpSentResponse;
import com.hyperlocal.delivery.dto.auth.PendingRegistrationPayload;
import com.hyperlocal.delivery.dto.auth.RegisterRequest;
import com.hyperlocal.delivery.dto.auth.TokenRefreshResponse;
import com.hyperlocal.delivery.exception.DuplicateEmailException;
import com.hyperlocal.delivery.exception.InvalidCredentialsException;
import com.hyperlocal.delivery.exception.InvalidRefreshTokenException;
import com.hyperlocal.delivery.exception.ErrorCode;
import com.hyperlocal.delivery.exception.OtpVerificationException;
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
import com.hyperlocal.delivery.security.JwtUtil;

/**
 * Unit tests for {@link AuthService} with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PendingRegistrationRepository pendingRegistrationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordResetMailer mailer;

    @Mock
    private MailProperties mailProperties;

    @Mock
    private OtpService otpService;

    @Mock
    private OtpEmailService otpEmailService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    @Mock
    private RegistrationRaceGuard registrationRaceGuard;

    @InjectMocks
    private AuthService authService;

    private Business testBusiness;
    private User testUser;

    @BeforeEach
    void setUp() {
        testBusiness = Business.builder()
                .id(1L)
                .name("Test Business")
                .email("owner@test.com")
                .passwordHash("$2a$12$encodedHash")
                .build();

        testUser = User.builder()
                .id(10L)
                .business(testBusiness)
                .email("owner@test.com")
                .passwordHash("$2a$12$encodedHash")
                .role(UserRole.BUSINESS_OWNER)
                .fullName("Test Owner")
                .phone("+91-9876543210")
                .isActive(true)
                .build();
    }

    // ── initiateRegistration tests ─────────────────────────────────────

    @Test
    void initiateRegistration_success() throws Exception {
        RegisterRequest req = new RegisterRequest(
                "New Business", "New Owner", "New@Test.com", "9000000000", "password123");

        // looked up under the normalized address, not the raw one
        when(businessRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$12$storedHash");
        when(objectMapper.writeValueAsString(any(PendingRegistrationPayload.class)))
                .thenReturn("{\"businessName\":\"New Business\"}");
        when(pendingRegistrationRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(otpService.generateOtp("new@test.com", OtpPurpose.REGISTRATION)).thenReturn("123456");

        OtpSentResponse response = authService.initiateRegistration(req);

        assertNotNull(response);
        assertEquals("Verification code sent to your email", response.message());
        assertEquals("ne***@test.com", response.email());

        // Verify pending registration was saved (via the race guard, so a
        // concurrent duplicate insert only rolls back its own isolated
        // transaction) with a 10-minute expiry
        ArgumentCaptor<PendingRegistration> captor = ArgumentCaptor.forClass(PendingRegistration.class);
        verify(registrationRaceGuard).savePendingRegistration(captor.capture());
        PendingRegistration saved = captor.getValue();
        assertEquals("new@test.com", saved.getEmail());
        assertEquals("{\"businessName\":\"New Business\"}", saved.getPayloadJson());
        assertNotNull(saved.getExpiresAt());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(9)));

        // Verify OTP was generated and email was sent
        verify(otpService).generateOtp("new@test.com", OtpPurpose.REGISTRATION);
        verify(otpEmailService).sendOtpEmail("new@test.com", "123456", OtpPurpose.REGISTRATION);
    }

    /**
     * An address that already has an account is rejected outright with a
     * clear "already registered" error, rather than the enumeration-safe
     * neutral response — see the rationale in AuthService.initiateRegistration.
     * Nothing is stored and no OTP is sent.
     */
    @Test
    void initiateRegistration_existingAccount_throwsDuplicateEmail() {
        RegisterRequest req = new RegisterRequest(
                "Dup Business", "Owner", "existing@test.com", "9000000000", "password123");

        when(businessRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> authService.initiateRegistration(req));
        verify(otpService, never()).generateOtp(any(), any());
        verify(otpEmailService, never()).sendOtpEmail(any(), any(), any());
        verify(registrationRaceGuard, never()).savePendingRegistration(any());
    }

    /**
     * The raw password must never reach pending_registrations. Serializing the
     * RegisterRequest directly used to write it to a plain TEXT column that
     * survives until the hourly cleanup job runs.
     */
    @Test
    void initiateRegistration_neverPersistsTheRawPassword() throws Exception {
        RegisterRequest req = new RegisterRequest(
                "New Business", "New Owner", "safe@test.com", "9000000000", "sup3rSecret!");

        when(passwordEncoder.encode("sup3rSecret!")).thenReturn("$2a$12$storedHash");
        when(pendingRegistrationRepository.findByEmail("safe@test.com")).thenReturn(Optional.empty());
        when(otpService.generateOtp("safe@test.com", OtpPurpose.REGISTRATION)).thenReturn("123456");

        // capture what would actually be serialized into payload_json
        ArgumentCaptor<PendingRegistrationPayload> payloadCaptor =
                ArgumentCaptor.forClass(PendingRegistrationPayload.class);
        when(objectMapper.writeValueAsString(payloadCaptor.capture())).thenReturn("{}");

        authService.initiateRegistration(req);

        PendingRegistrationPayload payload = payloadCaptor.getValue();
        assertEquals("$2a$12$storedHash", payload.passwordHash());
        assertNotEquals("sup3rSecret!", payload.passwordHash());
        assertFalse(payload.toString().contains("sup3rSecret!"),
                "the raw password must not survive anywhere in the stored payload");
    }

    @Test
    void initiateRegistration_existingPendingRegistration_overwrites() throws Exception {
        RegisterRequest req = new RegisterRequest(
                "Business", "Owner", "retry@test.com", "9000000000", "password123");

        PendingRegistration existing = PendingRegistration.builder()
                .id(5L)
                .email("retry@test.com")
                .payloadJson("{\"old\":\"data\"}")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(businessRepository.existsByEmail("retry@test.com")).thenReturn(false);
        when(userRepository.existsByEmail("retry@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$12$storedHash");
        when(objectMapper.writeValueAsString(any(PendingRegistrationPayload.class)))
                .thenReturn("{\"new\":\"data\"}");
        when(pendingRegistrationRepository.findByEmail("retry@test.com")).thenReturn(Optional.of(existing));
        when(otpService.generateOtp("retry@test.com", OtpPurpose.REGISTRATION)).thenReturn("654321");

        OtpSentResponse response = authService.initiateRegistration(req);

        assertNotNull(response);
        // Verify the existing record was updated (not a new one created)
        ArgumentCaptor<PendingRegistration> captor = ArgumentCaptor.forClass(PendingRegistration.class);
        verify(registrationRaceGuard).savePendingRegistration(captor.capture());
        PendingRegistration saved = captor.getValue();
        assertEquals(5L, saved.getId());
        assertEquals("{\"new\":\"data\"}", saved.getPayloadJson());
    }

    // ── account creation via OTP verification ─────────────────

    /**
     * The account is created from the hash stored at initiation time. The
     * encoder must NOT be called again here — there is no plaintext left to
     * encode, and re-encoding would produce a hash that matches nothing.
     */
    @Test
    void verifyRegistrationOtp_createsAccountFromStoredHash() {
        PendingRegistration pending = PendingRegistration.builder()
                .id(7L)
                .email("new@test.com")
                .payloadJson("{}")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        PendingRegistrationPayload payload = new PendingRegistrationPayload(
                "New Business", "New Owner", "new@test.com", "9000000000", "$2a$12$storedHash");

        when(otpService.validateOtp("new@test.com", "123456", OtpPurpose.REGISTRATION))
                .thenReturn(new OtpValidationResult.Success());
        when(pendingRegistrationRepository.findByEmail("new@test.com")).thenReturn(Optional.of(pending));
        when(objectMapper.readValue("{}", PendingRegistrationPayload.class)).thenReturn(payload);
        when(businessRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(registrationRaceGuard.createBusinessAndUser(
                eq("New Business"), eq("New Owner"), eq("new@test.com"), eq("9000000000"), anyString()))
                .thenAnswer(inv -> {
                    Business b = Business.builder().id(1L).name(inv.getArgument(0)).build();
                    return User.builder()
                            .id(10L)
                            .business(b)
                            .email(inv.getArgument(2))
                            .passwordHash(inv.getArgument(4))
                            .role(UserRole.BUSINESS_OWNER)
                            .fullName(inv.getArgument(1))
                            .phone(inv.getArgument(3))
                            .build();
                });
        when(jwtUtil.signAccess(any())).thenReturn("access-token");
        when(jwtUtil.generateOpaqueToken()).thenReturn("refresh-token");
        when(jwtUtil.accessTtl()).thenReturn(Duration.ofMinutes(60));
        when(jwtUtil.refreshTtl()).thenReturn(Duration.ofDays(7));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.verifyRegistrationOtp("New@Test.com", "123456");

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());

        ArgumentCaptor<String> passwordHashCaptor = ArgumentCaptor.forClass(String.class);
        verify(registrationRaceGuard).createBusinessAndUser(
                anyString(), anyString(), anyString(), anyString(), passwordHashCaptor.capture());
        assertEquals("$2a$12$storedHash", passwordHashCaptor.getValue());
        verify(passwordEncoder, never()).encode(any());
        verify(pendingRegistrationRepository).delete(pending);
    }

    /**
     * Someone else may have completed registration for this address between
     * the OTP being sent and it being verified. Throwing here is correct — the
     * caller has already proven they control the mailbox, so there is nothing
     * left to disclose.
     */
    @Test
    void verifyRegistrationOtp_addressTakenSinceOtpSent_throws() {
        PendingRegistration pending = PendingRegistration.builder()
                .id(8L)
                .email("existing@test.com")
                .payloadJson("{}")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        PendingRegistrationPayload payload = new PendingRegistrationPayload(
                "Dup Business", "Owner", "existing@test.com", "9000000000", "$2a$12$storedHash");

        when(otpService.validateOtp("existing@test.com", "123456", OtpPurpose.REGISTRATION))
                .thenReturn(new OtpValidationResult.Success());
        when(pendingRegistrationRepository.findByEmail("existing@test.com")).thenReturn(Optional.of(pending));
        when(objectMapper.readValue("{}", PendingRegistrationPayload.class)).thenReturn(payload);
        when(businessRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class,
                () -> authService.verifyRegistrationOtp("existing@test.com", "123456"));
        verify(registrationRaceGuard, never()).createBusinessAndUser(any(), any(), any(), any(), any());
    }

    /**
     * A row written before passwords were hashed at initiation has no
     * passwordHash. It must not become a 500, and it must not create an
     * account with a null hash — the user restarts registration instead.
     */
    @Test
    void verifyRegistrationOtp_payloadWithoutHash_isTreatedAsExpiredSession() {
        PendingRegistration pending = PendingRegistration.builder()
                .id(9L)
                .email("legacy@test.com")
                .payloadJson("{\"password\":\"plaintext\"}")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        PendingRegistrationPayload legacy = new PendingRegistrationPayload(
                "Old Business", "Owner", "legacy@test.com", "9000000000", null);

        when(otpService.validateOtp("legacy@test.com", "123456", OtpPurpose.REGISTRATION))
                .thenReturn(new OtpValidationResult.Success());
        when(pendingRegistrationRepository.findByEmail("legacy@test.com")).thenReturn(Optional.of(pending));
        when(objectMapper.readValue(anyString(), eq(PendingRegistrationPayload.class))).thenReturn(legacy);

        OtpVerificationException ex = assertThrows(OtpVerificationException.class,
                () -> authService.verifyRegistrationOtp("legacy@test.com", "123456"));
        assertEquals(ErrorCode.SESSION_EXPIRED, ex.getCode());
        verify(pendingRegistrationRepository).delete(pending);
        verify(registrationRaceGuard, never()).createBusinessAndUser(any(), any(), any(), any(), any());
    }

    // ── email normalization ────────────────────────────────

    /**
     * "New@Test.com" and "new@test.com" are the same account. Matching only
     * the raw string would let the second registration through as a new one.
     */
    @Test
    void initiateRegistration_duplicateDifferingOnlyInCase_isDetected() {
        RegisterRequest req = new RegisterRequest(
                "Business", "Owner", "  Existing@Test.com  ", "9000000000", "password123");

        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        // Only matches because AuthService normalized the address before
        // checking existsByEmail; a raw-string check would miss the stub
        // above and this would throw NPE/fail differently instead.
        assertThrows(DuplicateEmailException.class, () -> authService.initiateRegistration(req));
        verify(registrationRaceGuard, never()).savePendingRegistration(any());
        verify(otpEmailService, never()).sendOtpEmail(any(), any(), any());
    }

    /**
     * Accounts are stored under the normalized address, so login must look up
     * under it too — otherwise signing in with the casing you registered with
     * misses the row.
     */
    @Test
    void login_isCaseAndWhitespaceInsensitive() {
        LoginRequest req = new LoginRequest("  Owner@Test.com ", "password123");

        when(userRepository.findByEmailAndDeletedAtIsNull("owner@test.com"))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "$2a$12$encodedHash")).thenReturn(true);
        when(jwtUtil.signAccess(any())).thenReturn("access-token");
        when(jwtUtil.generateOpaqueToken()).thenReturn("refresh-token");
        when(jwtUtil.accessTtl()).thenReturn(Duration.ofMinutes(60));
        when(jwtUtil.refreshTtl()).thenReturn(Duration.ofDays(7));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("access-token", authService.login(req).accessToken());
        verify(loginRateLimiter).checkAllowed("owner@test.com");
    }

    // ── login tests ─────────────────────────────────────────────────────

    @Test
    void login_success() {
        LoginRequest req = new LoginRequest("owner@test.com", "password123");

        when(userRepository.findByEmailAndDeletedAtIsNull("owner@test.com"))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "$2a$12$encodedHash")).thenReturn(true);
        when(jwtUtil.signAccess(any())).thenReturn("access-token");
        when(jwtUtil.generateOpaqueToken()).thenReturn("refresh-token");
        when(jwtUtil.accessTtl()).thenReturn(Duration.ofMinutes(60));
        when(jwtUtil.refreshTtl()).thenReturn(Duration.ofDays(7));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login(req);

        assertNotNull(response);
        assertEquals("access-token", response.accessToken());
    }

    @Test
    void login_wrongPassword_throws() {
        LoginRequest req = new LoginRequest("owner@test.com", "wrongpassword");

        when(userRepository.findByEmailAndDeletedAtIsNull("owner@test.com"))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpassword", "$2a$12$encodedHash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(req));
    }

    @Test
    void login_inactiveAccount_throws() {
        testUser.setIsActive(false);
        LoginRequest req = new LoginRequest("owner@test.com", "password123");

        when(userRepository.findByEmailAndDeletedAtIsNull("owner@test.com"))
                .thenReturn(Optional.of(testUser));

        assertThrows(InvalidCredentialsException.class, () -> authService.login(req));
    }

    // ── refresh tests ────────────────────────────────────────────────────

    @Test
    void refresh_success() {
        String rawToken = "raw-refresh-token";
        String tokenHash = JwtUtil.sha256Hex(rawToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.deleteByTokenHash(tokenHash)).thenReturn(1L);
        when(jwtUtil.signAccess(any())).thenReturn("new-access-token");
        when(jwtUtil.generateOpaqueToken()).thenReturn("new-refresh-token");
        when(jwtUtil.accessTtl()).thenReturn(Duration.ofMinutes(60));
        when(jwtUtil.refreshTtl()).thenReturn(Duration.ofDays(7));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        TokenRefreshResponse response = authService.refresh(rawToken);

        assertNotNull(response);
        assertEquals("new-access-token", response.accessToken());
        assertEquals("new-refresh-token", response.refreshToken());
        verify(refreshTokenRepository).deleteByTokenHash(tokenHash);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    /**
     * Two requests racing the exact same refresh token: whichever's delete
     * actually removes the row wins and gets a new pair; the other's bulk
     * delete affects 0 rows (Mockito default for an unstubbed `long`), which
     * must be treated as "this token is no longer valid" rather than
     * silently proceeding to mint a second pair from one token.
     */
    @Test
    void refresh_concurrentReuseOfSameToken_secondCallerRejectedCleanly() {
        String rawToken = "raced-refresh-token";
        String tokenHash = JwtUtil.sha256Hex(rawToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(storedToken));
        // deleteByTokenHash left unstubbed -> Mockito's default for `long`
        // is 0, simulating "another request already deleted this row".

        assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh(rawToken));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void refresh_expired_throws() {
        String rawToken = "expired-refresh-token";
        String tokenHash = JwtUtil.sha256Hex(rawToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(storedToken));

        assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh(rawToken));
        verify(refreshTokenRepository).deleteByTokenHash(tokenHash);
    }

    @Test
    void refresh_unknown_throws() {
        String rawToken = "unknown-refresh-token";
        String tokenHash = JwtUtil.sha256Hex(rawToken);

        when(refreshTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.empty());

        assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh(rawToken));
    }

    // ── logout tests ─────────────────────────────────────────────────────

    @Test
    void logout_deletesTokens() {
        authService.logout(10L);
        verify(refreshTokenRepository).deleteByUser_Id(10L);
    }

    // ── resetPassword tests ─────────────────────────────────────────────

    @Test
    void resetPassword_validTokenAndPassword_updatesHashAndRevokesRefreshTokens() {
        String resetToken = "valid.jwt.token";
        String email = "owner@test.com";

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(email);
        // No iat → replay-protection branch is skipped.
        when(claims.getIssuedAt()).thenReturn(null);
        when(jwtUtil.parseResetTokenClaims(resetToken)).thenReturn(claims);
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newPassword1")).thenReturn("$2a$12$newEncodedHash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.resetPassword(resetToken, "newPassword1");

        assertEquals("$2a$12$newEncodedHash", testUser.getPasswordHash());
        assertNotNull(testUser.getPasswordChangedAt());
        verify(userRepository).save(testUser);
        verify(refreshTokenRepository).deleteByUser_Id(10L);
    }

    @Test
    void resetPassword_invalidToken_throwsInvalidResetTokenException() {
        when(jwtUtil.parseResetTokenClaims("bad-token"))
                .thenThrow(new JwtException("invalid"));

        assertThrows(com.hyperlocal.delivery.exception.InvalidResetTokenException.class,
                () -> authService.resetPassword("bad-token", "newPassword1"));

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).deleteByUser_Id(anyLong());
    }

    @Test
    void resetPassword_shortPassword_throwsValidationException() {
        String resetToken = "valid.jwt.token";
        String email = "owner@test.com";

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(email);
        when(claims.getIssuedAt()).thenReturn(null);
        when(jwtUtil.parseResetTokenClaims(resetToken)).thenReturn(claims);

        assertThrows(com.hyperlocal.delivery.exception.ValidationException.class,
                () -> authService.resetPassword(resetToken, "short"));

        // Token is NOT consumed — user can retry
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).deleteByUser_Id(anyLong());
    }

    @Test
    void resetPassword_nullPassword_throwsValidationException() {
        String resetToken = "valid.jwt.token";
        String email = "owner@test.com";

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(email);
        when(claims.getIssuedAt()).thenReturn(null);
        when(jwtUtil.parseResetTokenClaims(resetToken)).thenReturn(claims);

        assertThrows(com.hyperlocal.delivery.exception.ValidationException.class,
                () -> authService.resetPassword(resetToken, null));

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_userNotFound_throwsInvalidResetTokenException() {
        String resetToken = "valid.jwt.token";
        String email = "nobody@test.com";

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(email);
        when(claims.getIssuedAt()).thenReturn(null);
        when(jwtUtil.parseResetTokenClaims(resetToken)).thenReturn(claims);
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.empty());

        assertThrows(com.hyperlocal.delivery.exception.InvalidResetTokenException.class,
                () -> authService.resetPassword(resetToken, "newPassword1"));

        verify(refreshTokenRepository, never()).deleteByUser_Id(anyLong());
    }

    // ── maskEmail tests ──────────────────────────────────────────────────

    @Test
    void maskEmail_standardAddress() {
        assertEquals("ow***@test.com", AuthService.maskEmail("owner@test.com"));
    }

    @Test
    void maskEmail_shortLocalPart() {
        assertEquals("a***@x.com", AuthService.maskEmail("ab@x.com"));
    }

    @Test
    void maskEmail_singleCharLocalPart() {
        assertEquals("a***@x.com", AuthService.maskEmail("a@x.com"));
    }
}
