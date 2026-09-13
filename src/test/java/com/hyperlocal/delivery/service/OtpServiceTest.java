package com.hyperlocal.delivery.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hyperlocal.delivery.exception.ValidationException;
import com.hyperlocal.delivery.model.OtpPurpose;
import com.hyperlocal.delivery.model.OtpRecord;
import com.hyperlocal.delivery.model.OtpStatus;
import com.hyperlocal.delivery.repository.OtpRecordRepository;
import com.hyperlocal.delivery.security.JwtUtil;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpRecordRepository otpRecordRepository;

    @Mock
    private OtpRateLimiter otpRateLimiter;

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(otpRecordRepository, otpRateLimiter);
    }

    // ─── generateOtp tests ───────────────────────────────────────────

    @Test
    void generateOtp_validInput_returns6DigitCode() {
        String code = otpService.generateOtp("user@example.com", OtpPurpose.REGISTRATION);

        assertNotNull(code);
        assertEquals(6, code.length());
        int numericCode = Integer.parseInt(code);
        assertTrue(numericCode >= 100_000 && numericCode <= 999_999);
    }

    @Test
    void generateOtp_savesHashedCode_notPlaintext() {
        ArgumentCaptor<OtpRecord> captor = ArgumentCaptor.forClass(OtpRecord.class);

        String code = otpService.generateOtp("user@example.com", OtpPurpose.REGISTRATION);

        verify(otpRecordRepository).save(captor.capture());
        OtpRecord saved = captor.getValue();
        // The stored hash must NOT be the plaintext code
        assertNotEquals(code, saved.getCodeHash());
        // The hash should be 64 hex chars (SHA-256)
        assertEquals(64, saved.getCodeHash().length());
        // And it should match what JwtUtil.sha256Hex produces
        assertEquals(JwtUtil.sha256Hex(code), saved.getCodeHash());
    }

    @Test
    void generateOtp_normalizesEmail() {
        ArgumentCaptor<OtpRecord> captor = ArgumentCaptor.forClass(OtpRecord.class);

        otpService.generateOtp("  User@EXAMPLE.COM  ", OtpPurpose.PASSWORD_RESET);

        verify(otpRecordRepository).save(captor.capture());
        assertEquals("user@example.com", captor.getValue().getEmail());
    }

    @Test
    void generateOtp_invalidatesExistingActiveRecords() {
        otpService.generateOtp("user@example.com", OtpPurpose.REGISTRATION);

        verify(otpRecordRepository).invalidateActiveRecords("user@example.com", OtpPurpose.REGISTRATION);
    }

    @Test
    void generateOtp_nullEmail_throws() {
        assertThrows(ValidationException.class,
                () -> otpService.generateOtp(null, OtpPurpose.REGISTRATION));
    }

    @Test
    void generateOtp_malformedEmail_throws() {
        assertThrows(ValidationException.class,
                () -> otpService.generateOtp("not-an-email", OtpPurpose.REGISTRATION));
    }

    @Test
    void generateOtp_nullPurpose_throws() {
        assertThrows(ValidationException.class,
                () -> otpService.generateOtp("user@example.com", null));
    }

    @Test
    void generateOtp_setsCorrectExpiry() {
        ArgumentCaptor<OtpRecord> captor = ArgumentCaptor.forClass(OtpRecord.class);

        otpService.generateOtp("user@example.com", OtpPurpose.REGISTRATION);

        verify(otpRecordRepository).save(captor.capture());
        OtpRecord saved = captor.getValue();
        // Expiry should be ~5 minutes from now
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(4)));
        assertTrue(saved.getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(6)));
    }

    // ─── validateOtp tests ───────────────────────────────────────────

    @Test
    void validateOtp_correctCode_returnsSuccess() {
        String code = "123456";
        String codeHash = JwtUtil.sha256Hex(code);
        OtpRecord record = OtpRecord.builder()
                .email("user@example.com")
                .purpose(OtpPurpose.REGISTRATION)
                .codeHash(codeHash)
                .status(OtpStatus.ACTIVE)
                .attemptCount(0)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(otpRecordRepository.findActiveByEmailAndPurpose("user@example.com", OtpPurpose.REGISTRATION))
                .thenReturn(Optional.of(record));

        OtpValidationResult result = otpService.validateOtp("user@example.com", code, OtpPurpose.REGISTRATION);

        assertInstanceOf(OtpValidationResult.Success.class, result);
        assertEquals(OtpStatus.VERIFIED, record.getStatus());
    }

    @Test
    void validateOtp_noActiveRecord_returnsNoRecord() {
        when(otpRecordRepository.findActiveByEmailAndPurpose("user@example.com", OtpPurpose.REGISTRATION))
                .thenReturn(Optional.empty());

        OtpValidationResult result = otpService.validateOtp("user@example.com", "123456", OtpPurpose.REGISTRATION);

        assertInstanceOf(OtpValidationResult.Failure.class, result);
        assertEquals(OtpValidationResult.FailureReason.NO_RECORD,
                ((OtpValidationResult.Failure) result).reason());
    }

    @Test
    void validateOtp_expiredRecord_returnsExpired() {
        OtpRecord record = OtpRecord.builder()
                .email("user@example.com")
                .purpose(OtpPurpose.REGISTRATION)
                .codeHash(JwtUtil.sha256Hex("123456"))
                .status(OtpStatus.ACTIVE)
                .attemptCount(0)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(otpRecordRepository.findActiveByEmailAndPurpose("user@example.com", OtpPurpose.REGISTRATION))
                .thenReturn(Optional.of(record));

        OtpValidationResult result = otpService.validateOtp("user@example.com", "123456", OtpPurpose.REGISTRATION);

        assertInstanceOf(OtpValidationResult.Failure.class, result);
        assertEquals(OtpValidationResult.FailureReason.EXPIRED,
                ((OtpValidationResult.Failure) result).reason());
    }

    @Test
    void validateOtp_wrongCode_incrementsCounter() {
        OtpRecord record = OtpRecord.builder()
                .email("user@example.com")
                .purpose(OtpPurpose.REGISTRATION)
                .codeHash(JwtUtil.sha256Hex("123456"))
                .status(OtpStatus.ACTIVE)
                .attemptCount(0)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(otpRecordRepository.findActiveByEmailAndPurpose("user@example.com", OtpPurpose.REGISTRATION))
                .thenReturn(Optional.of(record));

        OtpValidationResult result = otpService.validateOtp("user@example.com", "999999", OtpPurpose.REGISTRATION);

        assertInstanceOf(OtpValidationResult.Failure.class, result);
        OtpValidationResult.Failure failure = (OtpValidationResult.Failure) result;
        assertEquals(OtpValidationResult.FailureReason.INVALID_CODE, failure.reason());
        assertEquals(4, failure.attemptsRemaining()); // 5 max - 1 attempt = 4 remaining
        assertEquals(1, record.getAttemptCount());
    }

    @Test
    void validateOtp_maxAttemptsReached_invalidatesRecord() {
        OtpRecord record = OtpRecord.builder()
                .email("user@example.com")
                .purpose(OtpPurpose.REGISTRATION)
                .codeHash(JwtUtil.sha256Hex("123456"))
                .status(OtpStatus.ACTIVE)
                .attemptCount(4) // one more wrong attempt will hit max (5)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(otpRecordRepository.findActiveByEmailAndPurpose("user@example.com", OtpPurpose.REGISTRATION))
                .thenReturn(Optional.of(record));

        OtpValidationResult result = otpService.validateOtp("user@example.com", "999999", OtpPurpose.REGISTRATION);

        assertInstanceOf(OtpValidationResult.Failure.class, result);
        OtpValidationResult.Failure failure = (OtpValidationResult.Failure) result;
        assertEquals(OtpValidationResult.FailureReason.MAX_ATTEMPTS, failure.reason());
        assertEquals(0, failure.attemptsRemaining());
        assertEquals(OtpStatus.INVALIDATED, record.getStatus());
    }

    @Test
    void validateOtp_alreadyAtMaxAttempts_returnsMaxAttempts() {
        OtpRecord record = OtpRecord.builder()
                .email("user@example.com")
                .purpose(OtpPurpose.REGISTRATION)
                .codeHash(JwtUtil.sha256Hex("123456"))
                .status(OtpStatus.ACTIVE)
                .attemptCount(5) // already at max
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        when(otpRecordRepository.findActiveByEmailAndPurpose("user@example.com", OtpPurpose.REGISTRATION))
                .thenReturn(Optional.of(record));

        OtpValidationResult result = otpService.validateOtp("user@example.com", "123456", OtpPurpose.REGISTRATION);

        assertInstanceOf(OtpValidationResult.Failure.class, result);
        assertEquals(OtpValidationResult.FailureReason.MAX_ATTEMPTS,
                ((OtpValidationResult.Failure) result).reason());
    }

    @Test
    void validateOtp_normalizesEmail() {
        when(otpRecordRepository.findActiveByEmailAndPurpose("user@example.com", OtpPurpose.REGISTRATION))
                .thenReturn(Optional.empty());

        otpService.validateOtp("  User@EXAMPLE.COM  ", "123456", OtpPurpose.REGISTRATION);

        verify(otpRecordRepository).findActiveByEmailAndPurpose("user@example.com", OtpPurpose.REGISTRATION);
    }
}
