import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { registerOwner, verifyRegistrationOtp, resendOtp } from '../api/authApi';
import { useAuth } from '../lib/AuthContext.tsx';
import { ApiError } from '../lib/apiClient';
import AuthShell from '../components/AuthShell.tsx';
import AuthSteps from '../components/AuthSteps.tsx';
import AuthHandoff from '../components/AuthHandoff.tsx';
import OtpInput from '../components/OtpInput.tsx';
import ResendCodeButton from '../components/ResendCodeButton.tsx';
import PasswordStrength from '../components/PasswordStrength.tsx';
import type { AuthResult, OtpErrorBody } from '../types/api';

/**
 * Owner sign-up. Two steps, because the backend splits them:
 *
 *   POST /auth/register                -> stores a pending registration, mails
 *                                         an OTP, returns NO tokens
 *   POST /auth/verify-registration-otp -> creates Business + User, returns tokens
 *
 * Until this page existed the only caller of those two endpoints was
 * OwnerAuthPage, which also served as the login screen. Collapsing login into
 * the unified /login took sign-up with it; this restores it as its own route.
 *
 * Agents do not register — an owner creates them — so this route is owner-only.
 * That is org structure, not something a person signing up needs explaining to
 * them on screen, so it is not in the copy.
 *
 * ── Redesign ────────────────────────────────────────────────────────────
 * Added the strength meter. "At least 8 characters" rewards `password` and
 * `12345678` identically, and this is the console that holds a shop's customer
 * addresses and phone numbers. It is advisory only — the server's minimum is
 * the control, and a client-side gate that disagreed with it would refuse a
 * password the API would have accepted.
 *
 * The two other audit items against this page were already built: the reveal
 * toggle has always been here, and `ResendCodeButton` has carried a 30-second
 * cooldown with `OtpInput` tracking an absolute expiry.
 */

/** Server-side minimum. Mirrored here so the user is told before a round trip. */
const MIN_PASSWORD = 8;

/** How long a mailed code stays valid. Mirrors the backend's OTP TTL. */
const OTP_TTL_MS = 5 * 60 * 1000;

type Step = 'details' | 'code';

export default function RegisterPage() {
  const navigate = useNavigate();
  const { setAuthSession } = useAuth();

  const [step, setStep] = useState<Step>('details');
  const [businessName, setBusinessName] = useState('');
  const [ownerName, setOwnerName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [reveal, setReveal] = useState(false);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<{ message: string; detail?: string } | null>(null);
  // Set only for a 409 DUPLICATE_EMAIL response, so the error box can offer
  // a "Sign in" link instead of just naming the problem.
  const [emailTaken, setEmailTaken] = useState(false);
  const [session, setSession] = useState<AuthResult | null>(null);

  // Remounts OtpInput so a resent code lands in cleared fields, rather than on
  // top of the digits that just failed. The expiry does NOT ride on this key —
  // see otpDeadline: remounting used to restart a five-minute countdown for a
  // code the server had already been holding for three.
  const [otpKey, setOtpKey] = useState(0);
  /** Absolute expiry of the code currently in the mailbox. Set when one is
   *  sent, and only moved by a resend. */
  const [otpDeadline, setOtpDeadline] = useState(0);

  const settledAt = useRef(0);

  async function submitDetails(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (password.length < MIN_PASSWORD) {
      setError({ message: `Use at least ${MIN_PASSWORD} characters for your password.` });
      return;
    }
    setBusy(true);
    setError(null);
    setEmailTaken(false);
    try {
      await registerOwner({ businessName, ownerName, email, phone, password });
      setOtpDeadline(Date.now() + OTP_TTL_MS);
      setStep('code');
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setEmailTaken(true);
        setError({ message: 'That email already has an account.' });
      } else {
        setError({ message: err instanceof Error && err.message ? err.message : 'Could not start registration. Try again.' });
      }
    } finally {
      setBusy(false);
    }
  }

  const submitCode = useCallback(async (code: string) => {
    setBusy(true);
    setError(null);
    try {
      const data = await verifyRegistrationOtp(email, code);
      setAuthSession({ token: data.token, role: data.role, user: data.user });
      settledAt.current = Date.now();
      setSession(data);
    } catch (err) {
      // A real rejection of the code is a 4xx with a body the OTP guard
      // produced (attemptsRemaining or reason). Anything else — a 5xx, or
      // status 0 for a request that never reached the server — has nothing
      // to do with what was typed, and treating it as a wrong code would
      // both lie about the cause and (via the otpKey remount below) throw
      // away six correct digits the user would otherwise not need to retype.
      if (err instanceof ApiError && err.status >= 400 && err.status < 500) {
        // The rejection body differs by which guard refused the code, so
        // every field is optional. Show the attempt budget when sent one.
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
      setBusy(false);
    }
  }, [email, setAuthSession]);

  const handleResend = useCallback(() => {
    setError(null);
    setOtpKey((k) => k + 1);
    setOtpDeadline(Date.now() + OTP_TTL_MS);
    resendOtp(email, 'REGISTRATION').catch(() =>
      setError({ message: 'Could not resend the code. Try again in a moment.' })
    );
  }, [email]);

  useEffect(() => {
    if (!session) return;
    const reduced =
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced) {
      navigate('/owner/shipments', { replace: true });
      return;
    }
    const remaining = Math.max(0, 1100 - (Date.now() - settledAt.current));
    const t = window.setTimeout(() => navigate('/owner/shipments', { replace: true }), remaining);
    return () => window.clearTimeout(t);
  }, [session, navigate]);

  if (session) {
    return <AuthShell><AuthHandoff session={session} /></AuthShell>;
  }

  return (
    <AuthShell>
      {step === 'details' ? (
        <>
          <AuthSteps current={1} total={2} />
          <h1>Create your <em>business account</em></h1>
          <p className="auth-lede">
            {/* F6: curly apostrophe — was a straight quote */}
            A few details, then we&rsquo;ll email you a code to confirm the address.
          </p>

          {error && (
            <AuthError message={error.message} detail={error.detail}>
              {emailTaken && <Link to="/login" className="auth-linkish">Sign in instead</Link>}
            </AuthError>
          )}

          <form onSubmit={submitDetails} noValidate>
            <Field id="reg-business" label="Business name" value={businessName}
              onChange={setBusinessName} placeholder="Nandini Stores" autoFocus
              autoComplete="organization" />
            <Field id="reg-owner" label="Your name" value={ownerName}
              onChange={setOwnerName} placeholder="Ravi Kumar" autoComplete="name" />
            <Field id="reg-email" label="Email" value={email} onChange={setEmail}
              type="email" inputMode="email" autoComplete="email" placeholder="you@yourbusiness.in" />
            <Field id="reg-phone" label="Phone" value={phone} onChange={setPhone}
              type="tel" inputMode="tel" autoComplete="tel" placeholder="+91 98455 20114" />

            <div className="auth-field">
              <label htmlFor="reg-password">Password</label>
              <div className="auth-input-wrap">
                <input
                  id="reg-password" type={reveal ? 'text' : 'password'}
                  autoComplete="new-password" required minLength={MIN_PASSWORD}
                  aria-describedby="reg-password-s reg-password-h"
                  value={password} onChange={(ev) => setPassword(ev.target.value)}
                />
                <button type="button" className="auth-reveal"
                  aria-label={reveal ? 'Hide password' : 'Show password'}
                  onClick={() => setReveal((v) => !v)}>
                  {reveal ? 'Hide' : 'Show'}
                </button>
              </div>
              {/* Advisory, not a gate. The hint below still states the rule the
                  server actually enforces. */}
              <PasswordStrength id="reg-password-s" value={password} min={MIN_PASSWORD} />
              <p className="auth-hint" id="reg-password-h">At least {MIN_PASSWORD} characters.</p>
            </div>

            <button className="auth-cta" type="submit" disabled={busy}>
              {busy ? 'Sending code…' : 'Send verification code'}
            </button>
          </form>

          <p className="auth-foot">
            Already have an account? <Link to="/login">Sign in</Link>
          </p>
        </>
      ) : (
        <>
          <AuthSteps current={2} total={2} />
          <h1>Enter the <em>six digits</em></h1>
          <p className="auth-lede">
            Sent to <b>{email}</b>.{' '}
            <button type="button" className="auth-linkish" onClick={() => { setStep('details'); setError(null); }}>
              Wrong address?
            </button>
          </p>

          {error && <AuthError message={error.message} detail={error.detail} />}

          <div className="auth-otp">
            <OtpInput key={otpKey} disabled={busy} expiresAt={otpDeadline} onComplete={submitCode} />
          </div>

          <div className="auth-resend">
            <ResendCodeButton onResend={handleResend} cooldownSeconds={30} />
          </div>

          {/* The one thing a code screen cannot fix by itself: a code that
              never arrived. Naming the sender is what gets it out of a spam
              folder, and it is cheaper than a support call. */}
          <p className="auth-hint auth-spam">
            Not there after a minute? Check spam — it comes from a no-reply address.
          </p>
        </>
      )}
    </AuthShell>
  );
}

function AuthError({
  message, detail, children
}: { message: string; detail?: string; children?: React.ReactNode }) {
  return (
    <div className="auth-msg" role="alert">
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
        strokeWidth="2.2" strokeLinecap="round" aria-hidden="true">
        <circle cx="12" cy="12" r="9" /><path d="M12 8v5M12 16.5v.01" />
      </svg>
      <div>
        <b>{message}</b>
        {detail && <span>{detail}</span>}
        {children}
      </div>
    </div>
  );
}

function Field({
  id, label, value, onChange, type = 'text', ...rest
}: {
  id: string; label: string; value: string; onChange: (v: string) => void; type?: string;
} & Omit<React.InputHTMLAttributes<HTMLInputElement>, 'id' | 'value' | 'onChange' | 'type'>) {
  return (
    <div className="auth-field">
      <label htmlFor={id}>{label}</label>
      <div className="auth-input-wrap">
        <input id={id} type={type} required value={value}
          onChange={(e) => onChange(e.target.value)} {...rest} />
      </div>
    </div>
  );
}
