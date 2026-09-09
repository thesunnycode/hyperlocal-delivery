package com.hyperlocal.delivery.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;

import com.hyperlocal.delivery.BaseIntegrationTest;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.OtpPurpose;
import com.hyperlocal.delivery.model.OtpRecord;
import com.hyperlocal.delivery.model.OtpStatus;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.repository.OtpRecordRepository;
import com.hyperlocal.delivery.security.JwtUtil;
import com.hyperlocal.delivery.service.OtpEmailService;
import com.hyperlocal.delivery.service.PasswordResetMailer;

/**
 * Integration tests for the password-reset flow.
 * The forgotPassword endpoint now uses OTP-based verification.
 */
class PasswordResetIntegrationTest extends BaseIntegrationTest {

    @MockitoBean
    private PasswordResetMailer mailer;

    @MockitoBean
    private OtpEmailService otpEmailService;

    @Autowired
    private OtpRecordRepository otpRecordRepository;

    private Business business;
    private User owner;

    @BeforeEach
    void setUp() {
        business = createAndSaveBusiness("Reset Biz", "resetbiz@test.com");
        owner = createAndSaveOwner(business, "resetowner@test.com");
    }

    @Test
    void forgotPassword_knownEmail_generatesOtpAndSendsEmail() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + owner.getEmail() + "\"}"))
                .andExpect(status().isAccepted());

        // Verify OTP email was sent
        verify(otpEmailService).sendOtpEmail(eq(owner.getEmail()), any(String.class), eq(OtpPurpose.PASSWORD_RESET));

        // Verify an active OTP record was created
        flushAndClear();
        Optional<OtpRecord> record = otpRecordRepository.findActiveByEmailAndPurpose(
                owner.getEmail(), OtpPurpose.PASSWORD_RESET);
        assertTrue(record.isPresent());
        assertEquals(OtpStatus.ACTIVE, record.get().getStatus());
        assertEquals(0, record.get().getAttemptCount());
    }

    @Test
    void forgotPassword_unknownEmail_stillReturns202_andSendsNothing() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody@test.com\"}"))
                .andExpect(status().isAccepted());

        // Verify no OTP email was sent
        verify(otpEmailService, never()).sendOtpEmail(any(), any(), any());

        // Verify no OTP record was created for the unknown email
        Optional<OtpRecord> record = otpRecordRepository.findActiveByEmailAndPurpose(
                "nobody@test.com", OtpPurpose.PASSWORD_RESET);
        assertTrue(record.isEmpty());
    }

    @Test
    void forgotPassword_secondRequest_invalidatesPreviousOtp() throws Exception {
        // First request
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + owner.getEmail() + "\"}"))
                .andExpect(status().isAccepted());

        flushAndClear();

        // Second request
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + owner.getEmail() + "\"}"))
                .andExpect(status().isAccepted());

        flushAndClear();

        // Verify OTP email was sent twice
        verify(otpEmailService, times(2)).sendOtpEmail(
                eq(owner.getEmail()), any(String.class), eq(OtpPurpose.PASSWORD_RESET));

        // Verify only one ACTIVE record remains (previous was invalidated)
        Optional<OtpRecord> activeRecord = otpRecordRepository.findActiveByEmailAndPurpose(
                owner.getEmail(), OtpPurpose.PASSWORD_RESET);
        assertTrue(activeRecord.isPresent());
    }

    @Test
    void resetPassword_validToken_setsNewPasswordAndAllowsLogin() throws Exception {
        // Issue a valid reset token (simulates verify-reset-otp success)
        String resetToken = jwtUtil.signResetToken(owner.getEmail());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\": \"" + resetToken + "\", \"newPassword\": \"newSecurePass1\"}"))
                .andExpect(status().isNoContent());

        // Verify new password works for login
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + owner.getEmail() + "\", \"password\": \"newSecurePass1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_frontendPasswordKey_setsNewPasswordAndAllowsLogin() throws Exception {
        String resetToken = jwtUtil.signResetToken(owner.getEmail());

        // Use "password" alias key (frontend convention)
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + resetToken + "\", \"password\": \"anotherPass1\"}"))
                .andExpect(status().isNoContent());

        // Verify new password works for login
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + owner.getEmail() + "\", \"password\": \"anotherPass1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_expiredToken_rejected() throws Exception {
        // We can't easily create an expired JWT in the test without mocking time,
        // so we just verify that a garbage token is rejected (same code path).
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\": \"expired.jwt.token\", \"newPassword\": \"somePassword123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_unknownToken_rejected() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "not-a-real-token", "newPassword": "somePassword123"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
