package com.hyperlocal.delivery.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hyperlocal.delivery.dto.auth.AuthResponse;
import com.hyperlocal.delivery.dto.auth.ForgotPasswordRequest;
import com.hyperlocal.delivery.dto.auth.LoginRequest;
import com.hyperlocal.delivery.dto.auth.OtpSentResponse;
import com.hyperlocal.delivery.dto.auth.RefreshRequest;
import com.hyperlocal.delivery.dto.auth.RegisterRequest;
import com.hyperlocal.delivery.dto.auth.ResendOtpRequest;
import com.hyperlocal.delivery.dto.auth.ResetPasswordRequest;
import com.hyperlocal.delivery.dto.auth.ResetTokenResponse;
import com.hyperlocal.delivery.dto.auth.TokenRefreshResponse;
import com.hyperlocal.delivery.dto.auth.UpdateAccountRequest;
import com.hyperlocal.delivery.dto.auth.UserResponse;
import com.hyperlocal.delivery.dto.auth.VerifyOtpRequest;
import com.hyperlocal.delivery.dto.common.ApiSuccess;
import com.hyperlocal.delivery.model.OtpPurpose;
import com.hyperlocal.delivery.repository.PendingRegistrationRepository;
import com.hyperlocal.delivery.repository.UserRepository;
import com.hyperlocal.delivery.dto.invite.AcceptInviteRequest;
import com.hyperlocal.delivery.dto.invite.InvitePreviewResponse;
import com.hyperlocal.delivery.security.CustomUserDetails;
import com.hyperlocal.delivery.service.AgentInviteService;
import com.hyperlocal.delivery.service.AuthService;
import com.hyperlocal.delivery.service.OtpEmailService;
import com.hyperlocal.delivery.service.OtpService;
import com.hyperlocal.delivery.util.ResponseBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST endpoints for authentication: registration, login, token refresh,
 * logout, and current-user profile retrieval.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;
    private final AgentInviteService agentInviteService;
    private final OtpService otpService;
    private final OtpEmailService otpEmailService;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final UserRepository userRepository;

    @PostMapping("/register")
    @Operation(summary = "Initiate registration with OTP email verification")
    @ApiResponses({
        @ApiResponse(responseCode = "200",
                description = "OTP sent if the address is free (never reveals whether it was)"),
        @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ApiSuccess<OtpSentResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseBuilder.success(authService.initiateRegistration(request));
    }

    @PostMapping("/verify-registration-otp")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Verify registration OTP and complete account creation")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created, tokens issued"),
        @ApiResponse(responseCode = "400", description = "OTP invalid, expired, max attempts, or session expired")
    })
    public ApiSuccess<AuthResponse> verifyRegistrationOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseBuilder.success(authService.verifyRegistrationOtp(request.email(), request.code()));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ApiSuccess<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseBuilder.success(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    public ApiSuccess<TokenRefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseBuilder.success(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke all refresh tokens for the current user")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Logged out successfully"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public void logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        authService.logout(userDetails.getUserId());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request a password-reset email")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Reset email sent if the account exists (never reveals which)")
    })
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
    }

    @PostMapping("/verify-reset-otp")
    @Operation(summary = "Verify password-reset OTP and receive a reset session token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OTP verified, reset session token issued"),
        @ApiResponse(responseCode = "400", description = "Invalid OTP, expired, or max attempts exceeded")
    })
    public ApiSuccess<ResetTokenResponse> verifyResetOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseBuilder.success(authService.verifyResetOtp(request.email(), request.code()));
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Redeem a password-reset token and set a new password")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password reset successfully"),
        @ApiResponse(responseCode = "400", description = "Reset token invalid, used, or expired")
    })
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.resetToken(), request.newPassword());
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend a new OTP code, invalidating any previous active code")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "New verification code sent"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ApiSuccess<OtpSentResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        // For REGISTRATION: only allow resend if there's an active pending
        // registration — prevents using this endpoint to spam arbitrary emails.
        // For PASSWORD_RESET: only allow if the email belongs to a real user
        // (safe — the forgot-password endpoint already non-discloses).
        if (request.purpose() == OtpPurpose.REGISTRATION) {
            if (pendingRegistrationRepository.findByEmail(normalizedEmail).isEmpty()) {
                // Return success to avoid email enumeration, but don't send
                return ResponseBuilder.success(
                        new OtpSentResponse("A new verification code has been sent", normalizedEmail));
            }
        } else if (request.purpose() == OtpPurpose.PASSWORD_RESET) {
            if (userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail).isEmpty()) {
                return ResponseBuilder.success(
                        new OtpSentResponse("A new verification code has been sent", normalizedEmail));
            }
        }

        String code = otpService.generateOtp(request.email(), request.purpose());
        otpEmailService.sendOtpEmail(request.email(), code, request.purpose());
        return ResponseBuilder.success(
                new OtpSentResponse("A new verification code has been sent", request.email()));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User profile retrieved"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiSuccess<UserResponse> me(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseBuilder.success(authService.me(userDetails.getUserId()));
    }

    /**
     * Read the public, minimal view of an invite so the setup screen can greet
     * the agent by name. Does not spend the invite — opening a link twice is
     * ordinary behaviour.
     */
    @GetMapping("/invite/{token}")
    @Operation(summary = "Preview an agent invite before it is accepted")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Invite is valid"),
        @ApiResponse(responseCode = "400", description = "Unknown, spent or expired invite")
    })
    public ApiSuccess<InvitePreviewResponse> previewInvite(@PathVariable String token) {
        return ResponseBuilder.success(agentInviteService.preview(token));
    }

    /**
     * Spend an invite and set the agent's password. Public by necessity: the
     * whole point is that the caller has no credentials yet — the token is
     * what stands in for them.
     */
    @PostMapping("/accept-invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set a password using an agent invite token")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password set; the agent can sign in"),
        @ApiResponse(responseCode = "400", description = "Unknown, spent or expired invite")
    })
    public void acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        agentInviteService.accept(request);
    }

    @PatchMapping("/me")
    @Operation(summary = "Update the current user's editable profile fields")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile updated"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ApiSuccess<UserResponse> updateMe(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateAccountRequest request) {
        return ResponseBuilder.success(authService.updateAccount(userDetails.getUserId(), request));
    }
}
