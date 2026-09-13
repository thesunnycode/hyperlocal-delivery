package com.hyperlocal.delivery.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hyperlocal.delivery.BaseIntegrationTest;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.OtpPurpose;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.service.OtpEmailService;

/**
 * Forgot-password must answer 202 every time, including once the OTP rate
 * limit is hit.
 *
 * <p>This test deliberately runs <em>without</em> the usual per-test
 * transaction. {@code AuthService.forgotPassword} swallows the rate-limit
 * exception thrown by {@code OtpService.generateOtp}; if both shared one
 * transaction, the swallowed exception would still mark that transaction
 * rollback-only and the commit would fail with a 500. Inside the normal
 * test transaction nothing is ever committed, so that failure is invisible,
 * which is why this class opts out and removes its own rows afterwards.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ForgotPasswordRateLimitIntegrationTest extends BaseIntegrationTest {

    private static final String EMAIL = "ratelimit-reset@test.com";

    /** Requests allowed per 15 minutes: {@code OtpRateLimiter.MAX_REQUESTS}. */
    private static final int LIMIT = 5;

    @MockitoBean
    private OtpEmailService otpEmailService;

    @Autowired
    private JdbcTemplate jdbc;

    private Business business;
    private User owner;

    @BeforeEach
    void setUp() {
        business = createAndSaveBusiness("Rate Limit Biz", "ratelimit-biz@test.com");
        owner = createAndSaveOwner(business, EMAIL);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM otp_records WHERE email = ?", EMAIL);
        jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
        jdbc.update("DELETE FROM businesses WHERE id = ?", business.getId());
    }

    @Test
    void forgotPassword_pastTheRateLimit_stillReturns202_andSendsNoMoreEmails() throws Exception {
        for (int i = 0; i < LIMIT + 2; i++) {
            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"" + EMAIL + "\"}"))
                    .andExpect(status().isAccepted());
        }

        // Only the requests inside the limit produced an email.
        verify(otpEmailService, times(LIMIT))
                .sendOtpEmail(eq(EMAIL), any(String.class), eq(OtpPurpose.PASSWORD_RESET));
    }
}
