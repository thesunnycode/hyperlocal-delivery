import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { registerOwner, verifyRegistrationOtp, resendOtp } from '../api/authApi';
import { useAuth } from '../lib/AuthContext.tsx';
import { ApiError } from '../lib/apiClient';
import { fieldErrorFrom } from '../utils/apiErrors';
import { useDocumentTitle } from '../lib/useDocumentTitle';
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

/** Backend field name (`ApiError.field`) -> the DOM id of the input that
 *  owns it, so a rejection can move focus to the actual offending field. */
const FIELD_ID: Record<string, string> = {
  businessName: 'reg-business',
  ownerName: 'reg-owner',
  email: 'reg-email',
  phone: 'reg-phone',
  businessPhone: 'reg-biz-phone',
  password: 'reg-password'
};
const fieldIdFor = (field: string) => FIELD_ID[field] ?? field;

export default function RegisterPage() {
  const navigate = useNavigate();
  const { setAuthSession } = useAuth();

  const [step, setStep] = useState<Step>('details');
  useDocumentTitle(step === 'details' ? 'Create your account' : 'Enter your code');
  const [businessName, setBusinessName] = useState('');
  const [ownerName, setOwnerName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  // Mirrors `phone` until the owner deliberately edits it — most shops give
  // customers the same number they were just asked for as "your name" is
  // one field up, so retyping it is the common case, not the exception.
  // Editing it independently (a landline, a second SIM) is one keystroke
  // away, not a second form.
  const [businessPhone, setBusinessPhone] = useState('');
  const [businessPhoneTouched, setBusinessPhoneTouched] = useState(false);
  const [password, setPassword] = useState('');
  const [reveal, setReveal] = useState(false);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<{ message: string; detail?: string } | null>(null);
  // Set only for a 409 DUPLICATE_EMAIL response, so the error box can offer
  // a "Sign in" link instead of just naming the problem.
  const [emailTaken, setEmailTaken] = useState(false);
  // Which field the last rejection named (GlobalExceptionHandler answers one
  // field at a time) — drives the inline message + border under that one
  // input instead of only a top banner naming a field the user then has to
  // find. Cleared as soon as any field changes.
  const [fieldError, setFieldError] = useState<{ field: string | null; message: string } | null>(null);
  const [session, setSession] = useState<AuthResult | null>(null);

  const fieldRefs = useRef<Record<string, HTMLInputElement | null>>({});

  const clearFieldError = () => setFieldError(null);

  // Remounts OtpInput so a resent code lands in cleared fields, rather than on
  // top of the digits that just failed. The expiry does NOT ride on this key —
  // see otpDeadline: remounting used to restart a five-minute countdown for a
  // code the server had already been holding for three.
  const [otpKey, setOtpKey] = useState(0);
  /** Absolute expiry of the code currently in the mailbox. Set when one is
   *  sent, and only moved by a resend. */
  const [otpDeadline, setOtpDeadline] = useState(0);

  const settledAt = useRef(0);

  // Prefills the business phone from the owner's own phone until the owner
  // deliberately edits it — see the field declaration above.
  useEffect(() => {
    if (!businessPhoneTouched) setBusinessPhone(phone);
  }, [phone, businessPhoneTouched]);

  async function submitDetails(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (password.length < MIN_PASSWORD) {
      setFieldError(null);
      setError({ message: `Use at least ${MIN_PASSWORD} characters for your password.` });
      fieldRefs.current['reg-password']?.focus();
      return;
    }
    setBusy(true);
    setError(null);
    setFieldError(null);
    setEmailTaken(false);
    const effectiveBusinessPhone = businessPhone.trim() || phone;
    try {
      await registerOwner({
        businessName, ownerName, email, phone, businessPhone: effectiveBusinessPhone, password
      });
      setOtpDeadline(Date.now() + OTP_TTL_MS);
      setStep('code');
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setEmailTaken(true);
        setError({ message: 'That email already has an account.' });
      } else {
        const fe = fieldErrorFrom(err);
        if (fe) {
          setFieldError(fe);
          if (fe.field) fieldRefs.current[fieldIdFor(fe.field)]?.focus();
        } else {
          setError({ message: err instanceof Error && err.message ? err.message : 'Could not start registration. Try again.' });
        }
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
              onChange={(v) => { setBusinessName(v); clearFieldError(); }}
              placeholder="Nandini Stores" autoFocus autoComplete="organization"
              error={fieldError?.field === 'businessName' ? fieldError.message : undefined}
              inputRef={(el) => { fieldRefs.current['reg-business'] = el; }} />
            <Field id="reg-owner" label="Your name" value={ownerName}
              onChange={(v) => { setOwnerName(v); clearFieldError(); }}
              placeholder="Ravi Kumar" autoComplete="name"
              error={fieldError?.field === 'ownerName' ? fieldError.message : undefined}
              inputRef={(el) => { fieldRefs.current['reg-owner'] = el; }} />
            <Field id="reg-email" label="Email" value={email}
              onChange={(v) => { setEmail(v); clearFieldError(); }}
              type="email" inputMode="email" autoComplete="email" placeholder="you@yourbusiness.in"
              error={fieldError?.field === 'email' ? fieldError.message : undefined}
              inputRef={(el) => { fieldRefs.current['reg-email'] = el; }} />
            <Field id="reg-phone" label="Your phone" value={phone}
              onChange={(v) => { setPhone(v); clearFieldError(); }}
              type="tel" inputMode="tel" autoComplete="tel" placeholder="+91 98455 20114"
              hint="Where support reaches you. Never shown to customers."
              error={fieldError?.field === 'phone' ? fieldError.message : undefined}
              inputRef={(el) => { fieldRefs.current['reg-phone'] = el; }} />
            <Field id="reg-biz-phone" label="Business phone" value={businessPhone}
              onChange={(v) => { setBusinessPhone(v); setBusinessPhoneTouched(true); clearFieldError(); }}
              type="tel" inputMode="tel" autoComplete="tel" placeholder="+91 98455 20114"
              hint="The number a customer is told to call if a delivery goes wrong. Same as your phone by default — edit it if it's different."
              error={fieldError?.field === 'businessPhone' ? fieldError.message : undefined}
              inputRef={(el) => { fieldRefs.current['reg-biz-phone'] = el; }} />

            <div className={`auth-field${fieldError?.field === 'password' ? ' has-error' : ''}`}>
              <label htmlFor="reg-password">Password</label>
              <div className="auth-input-wrap">
                <input
                  id="reg-password" type={reveal ? 'text' : 'password'}
                  ref={(el) => { fieldRefs.current['reg-password'] = el; }}
                  autoComplete="new-password" required minLength={MIN_PASSWORD}
                  aria-invalid={fieldError?.field === 'password' ? 'true' : undefined}
                  aria-describedby="reg-password-s reg-password-h"
                  value={password}
                  onChange={(ev) => { setPassword(ev.target.value); clearFieldError(); }}
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
              {fieldError?.field === 'password' ? (
                <p className="auth-field-err" id="reg-password-h" role="alert">
                  <AlertIcon size={14} /><span>{fieldError.message}</span>
                </p>
              ) : (
                <p className="auth-hint" id="reg-password-h">At least {MIN_PASSWORD} characters.</p>
              )}
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

function AlertIcon({ size = 17 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="2.2" strokeLinecap="round" aria-hidden="true">
      <circle cx="12" cy="12" r="9" /><path d="M12 8v5M12 16.5v.01" />
    </svg>
  );
}

function AuthError({
  message, detail, children
}: { message: string; detail?: string; children?: React.ReactNode }) {
  return (
    <div className="auth-msg" role="alert">
      <AlertIcon />
      <div>
        <b>{message}</b>
        {detail && <span>{detail}</span>}
        {children}
      </div>
    </div>
  );
}

/**
 * A labelled input wired for the two error styles this page needs: `error`
 * renders inline under the field, tints the border and sets `aria-invalid`
 * (the field the last rejection actually named); `hint` renders the same
 * slot when there is no error, so the two never stack.
 */
function Field({
  id, label, value, onChange, type = 'text', error, hint, inputRef, ...rest
}: {
  id: string; label: string; value: string; onChange: (v: string) => void; type?: string;
  error?: string; hint?: string; inputRef?: (el: HTMLInputElement | null) => void;
} & Omit<React.InputHTMLAttributes<HTMLInputElement>, 'id' | 'value' | 'onChange' | 'type'>) {
  return (
    <div className={`auth-field${error ? ' has-error' : ''}`}>
      <label htmlFor={id}>{label}</label>
      <div className="auth-input-wrap">
        <input id={id} type={type} required value={value} ref={inputRef}
          aria-invalid={error ? 'true' : undefined}
          aria-describedby={error || hint ? `${id}-note` : undefined}
          onChange={(e) => onChange(e.target.value)} {...rest} />
      </div>
      {error ? (
        <p className="auth-field-err" id={`${id}-note`} role="alert">
          <AlertIcon size={14} /><span>{error}</span>
        </p>
      ) : hint ? (
        <p className="auth-hint" id={`${id}-note`}>{hint}</p>
      ) : null}
    </div>
  );
}
