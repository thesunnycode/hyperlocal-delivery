import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  requestPasswordReset, verifyResetOtp, resetPassword, resendOtp,
  previewInvite, acceptInvite
} from '../api/authApi';
import { ApiError } from '../lib/apiClient';
import AuthShell from '../components/AuthShell.tsx';
import AuthSteps from '../components/AuthSteps.tsx';
import OtpInput from '../components/OtpInput.tsx';
import ResendCodeButton from '../components/ResendCodeButton.tsx';
import PasswordStrength from '../components/PasswordStrength.tsx';
import type { InvitePreview, OtpErrorBody } from '../types/api';

/**
 * Password reset, all three backend steps on one route:
 *
 *   POST /auth/forgot-password  -> 202, always the same answer
 *   POST /auth/verify-reset-otp -> { resetToken }
 *   POST /auth/reset-password   -> 204
 *
 * This replaces the four role-split pages (agent/owner x forgot/reset). A
 * reset does not care which role you are, and the old split meant a user had
 * to already know which of two URLs applied to them.
 *
 * Step 2 says "If <email> has an account, a code is on its way" whatever the
 * address. That is the backend's anti-enumeration behaviour surfaced honestly.
 * An earlier draft also explained WHY on screen; that is our reasoning, not the
 * user's problem, and it has been removed.
 *
 * `mode="setup"` mounts the same screens at /agent-setup for an agent setting
 * their first password, and takes one of two paths:
 *
 *   ?token=...  the invite path. The token proves the holder was sent the link
 *               by their owner, so there is no code to enter: preview the
 *               invite to greet them by name, then set the password. Two calls
 *               instead of three.
 *   ?email=...  the fallback, for an expired or lost link. Identical to a
 *               password reset — request a code to the mailbox, prove it, set
 *               the password — because for an account whose password nobody
 *               has ever seen, "reset" and "first sign-in" are the same act.
 *
 * The fallback still needs a deliberate tap to send the code. Firing a request
 * on page load would mean a forwarded link quietly issues codes.
 */

const MIN_PASSWORD = 8;

/** How long a mailed code stays valid. Mirrors the backend's OTP TTL. */
const OTP_TTL_MS = 5 * 60 * 1000;

type Step = 'email' | 'code' | 'password' | 'done';

/** Same two-tier shape as RegisterPage so error banners render consistently. */
type AuthErr = { message: string; detail?: string };

export default function ForgotPasswordPage({ mode = 'reset' }: { mode?: 'reset' | 'setup' }) {
  const [params] = useSearchParams();
  const setup = mode === 'setup';

  const inviteToken = setup ? params.get('token') : null;

  const [step, setStep] = useState<Step>(inviteToken ? 'password' : 'email');
  const [email, setEmail] = useState(setup ? (params.get('email') ?? '') : '');
  const [invite, setInvite] = useState<InvitePreview | null>(null);
  const [inviteDead, setInviteDead] = useState(false);
  /** Set when the invite check itself failed to reach the server — distinct
   *  from inviteDead, which means the server was reached and said no. */
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [resetToken, setResetToken] = useState('');
  const [password, setPassword] = useState('');
  const [reveal, setReveal] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<AuthErr | null>(null);
  const [otpKey, setOtpKey] = useState(0);
  /** Absolute expiry of the code in the mailbox — see RegisterPage. A rejected
   *  code remounts OtpInput; it must not hand the user five fresh minutes. */
  const [otpDeadline, setOtpDeadline] = useState(0);

  // Focus refs for managed transitions.
  const h1Ref = useRef<HTMLHeadingElement>(null);
  // F5: focus the "Go to sign in" link when the done step mounts.
  const doneRef = useRef<HTMLAnchorElement>(null);

  // Greet the agent by name, and catch a dead link before they have typed a
  // password rather than after.
  //
  // A 4xx here means the server looked the token up and said no — genuinely
  // dead. Anything else (a 5xx, or status 0 for a request that never reached
  // the server) says nothing about the invite itself, and treating it as
  // dead sent a rider with a perfectly valid link into the "ask for a new
  // one" fallback during, say, a deploy. inviteError keeps them on this step
  // with something they can retry instead.
  const checkInvite = useCallback((token: string) => {
    setInviteError(null);
    previewInvite(token)
      .then((p) => { setInvite(p); setEmail(p.email); })
      .catch((err) => {
        if (err instanceof ApiError && err.status >= 400 && err.status < 500) {
          setInviteDead(true);
          setStep('email');
        } else {
          setInviteError(err instanceof Error ? err.message : 'Could not check your invite link. Try again.');
        }
      });
  }, []);

  useEffect(() => {
    if (!inviteToken) return;
    checkInvite(inviteToken);
  }, [inviteToken, checkInvite]);

  // F2: move focus to the page heading when the invite preview resolves and
  // the full password form replaces the "Checking your link…" paragraph.
  useEffect(() => {
    if (invite) h1Ref.current?.focus();
  }, [invite]);

  // F5: move focus to the "Go to sign in" link when the done step appears so
  // a keyboard user does not have to Tab from <body> to reach the next action.
  useEffect(() => {
    if (step === 'done') doneRef.current?.focus();
  }, [step]);

  async function submitEmail(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await requestPasswordReset(email);
      setOtpDeadline(Date.now() + OTP_TTL_MS);
      setStep('code');
    } catch {
      setError({ message: 'Could not send the code. Try again in a moment.' });
    } finally {
      setBusy(false);
    }
  }

  const submitCode = useCallback(async (code: string) => {
    setBusy(true);
    setError(null);
    try {
      const { resetToken: tk } = await verifyResetOtp(email, code);
      setResetToken(tk);
      setStep('password');
    } catch (err) {
      // A 4xx is a real rejection from the OTP guard; anything else (a 5xx,
      // or status 0 for a request that never reached the server) has nothing
      // to do with the code itself, and remounting OtpInput below would
      // throw away six correct digits for no reason.
      if (err instanceof ApiError && err.status >= 400 && err.status < 500) {
        // F1: split the OTP error into headline + detail so the attempt
        // count has a different visual weight from the error label.
        const body = err.body as OtpErrorBody | null;
        const left = body?.attemptsRemaining;
        setError(
          typeof left === 'number'
            ? { message: 'That code is not right.', detail: `${left} ${left === 1 ? 'attempt' : 'attempts'} remaining.` }
            : { message: body?.reason ?? 'That code is not right. Check it and try again.' }
        );
        setOtpKey((k) => k + 1);
      } else {
        setError({ message: err instanceof Error ? err.message : 'Something went wrong. Try again.' });
      }
    } finally {
      setBusy(false);
    }
  }, [email]);

  async function submitPassword(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (password.length < MIN_PASSWORD) {
      setError({ message: `Use at least ${MIN_PASSWORD} characters.` });
      return;
    }
    setBusy(true);
    setError(null);
    try {
      if (inviteToken) await acceptInvite(inviteToken, password);
      else await resetPassword(resetToken, password);
      setStep('done');
    } catch (err) {
      // A 4xx means the server rejected the token/code itself — the specific
      // diagnosis below is right. Anything else (a 5xx, or a request that
      // never reached the server) has nothing to do with the token, and
      // telling someone to start the whole flow over for a passing outage
      // is a worse outcome than just asking them to try again.
      if (err instanceof ApiError && err.status >= 400 && err.status < 500) {
        setError({ message: inviteToken
          ? 'Could not set the password. This link may have been used already — ask for a new one.'
          : 'Could not set the new password. The code may have expired — start again.'
        });
      } else {
        setError({ message: err instanceof Error ? err.message : 'Something went wrong. Try again.' });
      }
    } finally {
      setBusy(false);
    }
  }

  const handleResend = useCallback(() => {
    setError(null);
    setOtpKey((k) => k + 1);
    setOtpDeadline(Date.now() + OTP_TTL_MS);
    resendOtp(email, 'PASSWORD_RESET').catch(() =>
      setError({ message: 'Could not resend the code. Try again in a moment.' })
    );
  }, [email]);

  return (
    <AuthShell>
      {error && <AuthError message={error.message} detail={error.detail} />}

      {inviteDead && step === 'email' && (
        <div className="auth-msg auth-msg-top" role="status">
          <div>
            <b>That link has already been used, or it expired.</b> You can still get in: enter
            the email your account was created with and we will send you a code.
          </div>
        </div>
      )}

      {step === 'email' && (
        <>
          <AuthSteps current={1} total={3} />
          <h1>{setup ? <>Set up your <em>account</em></> : <>Reset your <em>password</em></>}</h1>
          <p className="auth-lede">
            {setup
              ? 'Your business has already created your account. Confirm the email it was created with and we will send you a six-digit code.'
              /* F6: was "we'll" with a straight apostrophe */
              : "Enter the email on your account and we\u2019ll send you a six-digit code."}
          </p>
          <form onSubmit={submitEmail} noValidate>
            <div className="auth-field">
              <label htmlFor="fp-email">Email</label>
              <div className="auth-input-wrap">
                <input id="fp-email" type="email" inputMode="email" autoComplete="email"
                  placeholder="you@yourbusiness.in" required autoFocus
                  value={email} onChange={(ev) => setEmail(ev.target.value)} />
              </div>
            </div>
            <button className="auth-cta" type="submit" disabled={busy}>
              {busy ? 'Sending…' : (setup ? 'Send my code' : 'Send reset code')}
            </button>
          </form>
          <p className="auth-foot">
            {setup
              ? <>Already set a password? <Link to="/login">Sign in</Link></>
              : <>Remembered it? <Link to="/login">Back to sign in</Link></>}
          </p>
        </>
      )}

      {step === 'code' && (
        <>
          <AuthSteps current={2} total={3} />
          <h1>Enter the <em>six digits</em></h1>
          <p className="auth-lede">
            If <b>{email}</b> has an account, a code is on its way.{' '}
            <button type="button" className="auth-linkish"
              onClick={() => { setStep('email'); setError(null); }}>Wrong address?</button>
          </p>
          <div className="auth-otp">
            <OtpInput key={otpKey} disabled={busy} expiresAt={otpDeadline} onComplete={submitCode} />
          </div>
          <div className="auth-resend">
            <ResendCodeButton onResend={handleResend} cooldownSeconds={30} />
          </div>
        </>
      )}

      {/* F2: aria-live so the transition from "Checking…" to the form is
          announced. The div is always present when on the password step so
          content changes inside it are picked up by the live region. */}
      {step === 'password' && (
        <div aria-live="polite">
          {inviteToken && !invite && !inviteDead ? (
            inviteError ? (
              <>
                <h1>Couldn&rsquo;t check your link</h1>
                <p className="auth-lede">{inviteError}</p>
                <button type="button" className="auth-cta" onClick={() => checkInvite(inviteToken)}>
                  Try again
                </button>
              </>
            ) : (
              <p className="auth-lede">Checking your link&hellip;</p>
            )
          ) : !(inviteToken && !invite) && (
            <>
              {/* F3: pass current=1 total=1 on the invite path so the segment
                  bar renders and AT has a progressbar landmark. */}
              {inviteToken
                ? <AuthSteps current={1} total={1} label="Almost there" />
                : <AuthSteps current={3} total={3} />}
              {/* F2: tabIndex=-1 so .focus() works on a non-interactive element */}
              <h1 ref={h1Ref} tabIndex={-1}>
                {invite
                  /* F4: || invite.fullName fallback for empty/whitespace fullName */
                  ? <>Welcome, <em>{invite.fullName.split(' ')[0] || invite.fullName}</em></>
                  : setup ? <>Choose <em>a password</em></> : <>Choose <em>a new password</em></>}
              </h1>
              <p className="auth-lede">
                {invite
                  ? <>{invite.businessName ?? 'Your business'} set up your delivery account. Choose a password and it is yours — they cannot see it.</>
                  : setup
                    ? 'This is what you will sign in with from now on. Keep it to yourself — your owner cannot see it.'
                    : 'Choosing a new password signs you out on your other devices.'}
              </p>
              {invite && (
                <p className="auth-hint auth-hint-id">
                  You will sign in with <b>{invite.email}</b>.
                </p>
              )}
              <form onSubmit={submitPassword} noValidate>
                <div className="auth-field">
                  <label htmlFor="fp-password">{setup ? 'Password' : 'New password'}</label>
                  <div className="auth-input-wrap">
                    <input id="fp-password" type={reveal ? 'text' : 'password'}
                      autoComplete="new-password" required minLength={MIN_PASSWORD} autoFocus
                      aria-describedby="fp-password-s fp-password-h"
                      value={password} onChange={(ev) => setPassword(ev.target.value)} />
                    <button type="button" className="auth-reveal"
                      aria-label={reveal ? 'Hide password' : 'Show password'}
                      onClick={() => setReveal((v) => !v)}>
                      {reveal ? 'Hide' : 'Show'}
                    </button>
                  </div>
                  {/* Advisory, not a gate — see RegisterPage, which this
                      mirrors. This is the screen a rider sets their permanent
                      password on via an invite link; the same nudge applies. */}
                  <PasswordStrength id="fp-password-s" value={password} min={MIN_PASSWORD} />
                  <p className="auth-hint" id="fp-password-h">At least {MIN_PASSWORD} characters.</p>
                </div>
                <button className="auth-cta" type="submit" disabled={busy}>
                  {busy ? 'Saving…' : 'Set password'}
                </button>
              </form>
            </>
          )}
        </div>
      )}

      {step === 'done' && (
        <div aria-live="polite">
          <h1>{setup ? <>You&rsquo;re <em>all set</em></> : <>Password <em>changed</em></>}</h1>
          <p className="auth-lede">
            {setup
              ? 'Sign in and your deliveries for today will be waiting.'
              : 'You can sign in with your new password now.'}
          </p>
          {/* F5+F7: Link (not button+navigate) so AT reads it as navigation,
              and ref so focus lands here when the done step mounts. */}
          <Link
            to="/login"
            replace
            className="auth-cta"
            ref={doneRef}
          >
            Go to sign in
          </Link>
        </div>
      )}
    </AuthShell>
  );
}

function AuthError({ message, detail }: { message: string; detail?: string }) {
  return (
    <div className="auth-msg auth-msg-top" role="alert">
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
        strokeWidth="2.2" strokeLinecap="round" aria-hidden="true">
        <circle cx="12" cy="12" r="9" /><path d="M12 8v5M12 16.5v.01" />
      </svg>
      <div>
        <b>{message}</b>
        {detail && <span>{detail}</span>}
      </div>
    </div>
  );
}
