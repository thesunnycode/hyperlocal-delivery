package com.hyperlocal.delivery.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.hyperlocal.delivery.BaseIntegrationTest;
import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.model.UserRole;

/**
 * Integration tests for the auth login/register response shape — specifically
 * the top-level {@code token} and {@code role} aliases the frontend's
 * AuthContext destructures directly (data.token, data.role, data.user).
 */
class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loginResponseIncludesTopLevelTokenAndRole() throws Exception {
        // Create a user with a known password so we can log in
        Business business = createAndSaveBusiness("Token Role Biz", "tokenrolebiz@test.com");
        User owner = User.builder()
                .business(business)
                .email("owner@test.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(UserRole.BUSINESS_OWNER)
                .fullName("Token Role Owner")
                .phone("+91-9000000042")
                .build();
        userRepository.save(owner);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.role").value("OWNER"))
                .andExpect(jsonPath("$.data.user.email").value("owner@test.com"));
    }

    /**
     * Regression test for the {@code UserResponse.fullName -> name} rename:
     * every login/register/me response embeds a {@code user} object, and the
     * frontend reads {@code user.name} (OwnerAccountPage, OwnerLayout,
     * AdminLayout, AgentAssignmentsPage) — never {@code user.fullName}.
     *
     * <p>With the OTP-based registration flow, the register endpoint no longer
     * returns user data directly. We verify the field name via the login
     * response instead.
     */
    @Test
    void registerResponseExposesUserNameNotFullName() throws Exception {
        // The register endpoint now returns OtpSentResponse (no user object).
        // Verify user.name field via login response instead, which uses the
        // same AuthResponse/UserResponse shape that verify-registration-otp returns.
        Business business = createAndSaveBusiness("Name Field Biz", "namefieldbiz@test.com");
        User owner = User.builder()
                .business(business)
                .email("nameowner@test.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(UserRole.BUSINESS_OWNER)
                .fullName("Name Field Owner")
                .phone("+91-9000000043")
                .build();
        userRepository.save(owner);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nameowner@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.name").value("Name Field Owner"))
                .andExpect(jsonPath("$.data.user.fullName").doesNotExist());
    }

    @Test
    void loginResponseExposesUserNameNotFullName() throws Exception {
        Business business = createAndSaveBusiness("Login Name Biz", "loginnamebiz@test.com");
        User owner = User.builder()
                .business(business)
                .email("loginnameowner@test.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(UserRole.BUSINESS_OWNER)
                .fullName("Login Name Owner")
                .phone("+91-9000000044")
                .build();
        userRepository.save(owner);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"loginnameowner@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.name").value("Login Name Owner"))
                .andExpect(jsonPath("$.data.user.fullName").doesNotExist());
    }

    @Test
    void meResponseExposesUserNameNotFullName() throws Exception {
        Business business = createAndSaveBusiness("Me Name Biz", "menamebiz@test.com");
        User owner = createAndSaveOwner(business, "menameowner@test.com");
        String token = tokenFor(owner);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(owner.getFullName()))
                .andExpect(jsonPath("$.data.fullName").doesNotExist());
    }
}
