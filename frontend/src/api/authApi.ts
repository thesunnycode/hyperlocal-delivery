import { apiFetch } from '../lib/apiClient';
import type {
  AuthResult,
  EmptyResponse,
  ForgotPasswordBody,
  InvitePreview,
  LoginBody,
  OtpPurpose,
  OtpSent,
  RegisterBody,
  ResendOtpBody,
  ResetPasswordBody,
  ResetToken,
  UpdateAccountBody,
  User,
  VerifyOtpBody
} from '../types/api';

/* The request-body types below are applied with `satisfies` rather than as
   parameter types: the public signatures stay positional for their callers,
   but each body literal is now checked against the shape mirrored from the
   Java record. They used to be declared in types/api.ts and referenced
   nowhere, so a rename on the backend would not have shown up here. */

// POST /api/auth/login -> { token, role: 'OWNER'|'AGENT', user: {id,name,email} }
export function login(email: string, password: string) {
  return apiFetch<AuthResult>('/auth/login', {
    method: 'POST',
    body: { email, password } satisfies LoginBody,
    skipAuth: true
  });
}

// POST /api/auth/register — validates input, stores pending registration, sends OTP.
// Returns { message, email } (no tokens until OTP verified).
export function registerOwner({
  businessName,
  ownerName,
  email,
  phone,
  password
}: RegisterBody) {
  return apiFetch<OtpSent>('/auth/register', {
    method: 'POST',
    body: { businessName, ownerName, email, phone, password },
    skipAuth: true
  });
}

// POST /api/auth/verify-registration-otp — verifies OTP, creates Business + User, returns tokens.
// Returns { accessToken, refreshToken, expiresIn, user }
export function verifyRegistrationOtp(email: string, code: string) {
  return apiFetch<AuthResult>('/auth/verify-registration-otp', {
    method: 'POST',
    body: { email, code } satisfies VerifyOtpBody,
    skipAuth: true
  });
}

// POST /api/auth/resend-otp — regenerates OTP for active pending registration or reset.
// Returns { message, email }
export function resendOtp(email: string, purpose: OtpPurpose) {
  return apiFetch<OtpSent>('/auth/resend-otp', {
    method: 'POST',
    body: { email, purpose } satisfies ResendOtpBody,
    skipAuth: true
  });
}

// POST /api/auth/forgot-password — same response whether or not the address
// exists (no account enumeration); backend needs a mail sender behind this.
// Answers 202 with no body, so this resolves to '' rather than null.
export function requestPasswordReset(email: string) {
  return apiFetch<EmptyResponse>('/auth/forgot-password', {
    method: 'POST',
    body: { email } satisfies ForgotPasswordBody,
    skipAuth: true
  });
}

// POST /api/auth/verify-reset-otp — verifies OTP for password reset, returns { resetToken }.
export function verifyResetOtp(email: string, code: string) {
  return apiFetch<ResetToken>('/auth/verify-reset-otp', {
    method: 'POST',
    body: { email, code } satisfies VerifyOtpBody,
    skipAuth: true
  });
}

// POST /api/auth/reset-password — accepts resetToken (JWT) + newPassword.
// Answers 204, so this resolves to null.
export function resetPassword(resetToken: string, newPassword: string) {
  return apiFetch<EmptyResponse>('/auth/reset-password', {
    method: 'POST',
    body: { resetToken, newPassword } satisfies ResetPasswordBody,
    skipAuth: true
  });
}

export function me() {
  return apiFetch<User>('/auth/me');
}

/**
 * `PATCH /api/auth/me` — the editable slice of the signed-in account.
 *
 * Every field is optional; the server applies only the ones present, which is
 * why this is a Partial and not the whole User. `businessName` and
 * `businessPhone` are silently ignored for a delivery agent (AuthService
 * checks the role), so the agent UI simply never offers them. Email and role
 * are not editable and the request record has no fields for them.
 */
export function updateMe(patch: UpdateAccountBody) {
  return apiFetch<User>('/auth/me', { method: 'PATCH', body: patch });
}

/**
 * `GET /auth/invite/:token` — who the invite is for, so the setup screen can
 * greet them. Public, and reading it does not spend the invite.
 */
export function previewInvite(token: string) {
  return apiFetch<InvitePreview>(`/auth/invite/${encodeURIComponent(token)}`, { skipAuth: true });
}

/** `POST /auth/accept-invite` — spend the invite and set the password. */
export function acceptInvite(token: string, password: string) {
  return apiFetch<void>('/auth/accept-invite', {
    method: 'POST',
    body: { token, password },
    skipAuth: true
  });
}
