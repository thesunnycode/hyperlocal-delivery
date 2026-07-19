package com.hyperlocal.delivery.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for business registration. Initiates the OTP verification flow
 * by validating all fields, storing a pending registration payload, and sending
 * a 6-digit OTP code to the provided email address.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 1, max = 100) String businessName,
        @NotBlank @Size(min = 1, max = 100) String ownerName,
        @NotBlank @Email String email,
        // Same pattern as every other phone field (CreateAgentRequest,
        // UpdateAccountRequest, CreateShipmentRequest): spaces are allowed,
        // so the exact placeholder shown on the form ("+91 98455 20114")
        // passes its own validation.
        @NotBlank @Pattern(regexp = "[+\\d\\s\\-]{7,20}") String phone,
        // The number the tracking page tells a customer to call when a
        // delivery goes wrong. Asked for at sign-up (not left to be filled in
        // later on Account, where nothing ever prompted for it) so the call
        // button on /track/:token is never silently missing.
        @NotBlank @Pattern(regexp = "[+\\d\\s\\-]{7,20}") String businessPhone,
        @NotBlank @Size(min = 8) String password
) {}
