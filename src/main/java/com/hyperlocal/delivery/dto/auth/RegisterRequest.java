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
        @NotBlank @Pattern(regexp = "\\+?[\\d\\-]{7,15}") String phone,
        @NotBlank @Size(min = 8) String password
) {}
