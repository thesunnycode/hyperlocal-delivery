package com.hyperlocal.delivery.dto.auth;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for editing the current user's own profile (X3). Full name,
 * phone, and (owner-only) business name are editable — email (login) and
 * role never are, because this record simply has no fields for them. All
 * fields are optional: a null field leaves the existing value unchanged
 * (partial update). {@code businessName} is applied only when the caller is
 * a {@code BUSINESS_OWNER}; a delivery agent sending it has it silently
 * ignored (see {@code AuthService#updateAccount}).
 *
 * <p>The {@code @Pattern(".*\\S.*")} guard on the free-text fields exists
 * because dropping {@code @NotBlank} for partial-update semantics would
 * otherwise let a whitespace-only value (e.g. {@code "  "}) through
 * {@code @Size(min = 2)}.
 */
public record UpdateAccountRequest(
        @Pattern(regexp = ".*\\S.*") @Size(min = 2, max = 100) String fullName,
        @Pattern(regexp = "[+\\d\\s\\-]{7,20}") String phone,
        @Pattern(regexp = ".*\\S.*") @Size(min = 2, max = 200) String businessName,
        @Pattern(regexp = "[+\\d\\s\\-]{7,20}") String businessPhone
) {}
