import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/AuthContext.tsx';
import { ApiError } from '../lib/apiClient';
import AuthShell from '../components/AuthShell.tsx';
import AuthHandoff from '../components/AuthHandoff.tsx';
import type { AuthResult } from '../types/api';

/**
 * The single sign-in door.
 *
 * This replaced AgentLoginPage and OwnerAuthPage. Those hard-coded an
 * `expectedRole` into `login()`, so an owner typing a correct password at
 * /agent/login was rejected and had to know which of two URLs applied. The
 * response's own role decides now, and both `expectedRole` and
 * RoleMismatchError have been deleted from AuthContext along with the second
 * page — that failure mode no longer exists to handle.
 *
 * The handoff card between submit and navigation is deliberate, not a fake
 * spinner — it names who you signed in as before the app changes under you.
 * It waits out MIN_HANDOFF_MS only if the request beat the animation, so it
 * never adds delay to a slow login, and it is skipped entirely when the user
 * prefers reduced motion.
 */

/** Long enough for the staged reveal to land; only ever applied to time the
 *  network request did not already consume. */
const MIN_HANDOFF_MS = 1100;

const destinationFor = (role: string) =>
  role === 'OWNER' ? '/owner/shipments' : '/agent/assignments';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [reveal, setReveal] = useState(false);
  const [busy, setBusy] = useState(false);
  const [rejected, setRejected] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [session, setSession] = useState<AuthResult | null>(null);

  /* Caps Lock is the most common cause of a password the user is certain is
     correct, and the one thing this screen can tell them that the server
     cannot — the rejection deliberately does not say which field was wrong.
     Read from the keyboard event, and only while the password field has
     focus: a global listener would announce it on a page with nothing to
     type into. */
  const [caps, setCaps] = useState(false);

  // Set when the successful response landed, so the handoff only pads the
  // time the request did not already spend.
  const settledAt = useRef(0);

  async function signIn(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setRejected(false);
    setServerError(null);
    try {
      // No expectedRole: the response decides where this person belongs.
      const data = await login(email, password);
      settledAt.current = Date.now();
      setSession(data);
    } catch (err) {
      // 401 is the one status that means "wrong credentials" — apiClient
      // collapses 401/403 into a bodyless ApiError, so that's the only case
      // worth reading .status for here. Anything else (a 5xx, or status 0 for
      // a request that never reached the server) is not a credentials
      // problem, and telling the user their password is wrong when the real
      // issue is that the server is unreachable sends them retyping a
      // password that was already correct.
      if (err instanceof ApiError && err.status === 401) {
        // Deliberately not naming which field was wrong — telling an
        // attacker that the email exists is a disclosure. Both values are kept.
        setRejected(true);
      } else {
        setServerError(err instanceof Error ? err.message : 'Something went wrong. Try again.');
      }
      setBusy(false);
    }
  }

  useEffect(() => {
    if (!session) return;
    const to = destinationFor(session.role);

    const reduced =
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced) {
      navigate(to, { replace: true });
      return;
    }

    const remaining = Math.max(0, MIN_HANDOFF_MS - (Date.now() - settledAt.current));
    const timer = window.setTimeout(() => navigate(to, { replace: true }), remaining);
    return () => window.clearTimeout(timer);
  }, [session, navigate]);

  return (
    <AuthShell
      pitch={{
        heading: 'Good to see you again.',
        body: 'Sign in to pick up where you left off — today’s queue, the roster, and every shipment in between.'
      }}
    >
      {session ? <AuthHandoff session={session} /> : (
              <>
                <h1>Welcome <em>back</em></h1>

                {rejected && (
                  <div className="auth-msg" role="alert">
                    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                      strokeWidth="2.2" strokeLinecap="round" aria-hidden="true">
                      <circle cx="12" cy="12" r="9" /><path d="M12 8v5M12 16.5v.01" />
                    </svg>
                    <div>
                      <b>Email or password is incorrect</b>
                      <span>Both fields kept what you typed. Neither is singled out — that is deliberate.</span>
                    </div>
                  </div>
                )}

                {serverError && (
                  <div className="auth-msg" role="alert">
                    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                      strokeWidth="2.2" strokeLinecap="round" aria-hidden="true">
                      <circle cx="12" cy="12" r="9" /><path d="M12 8v5M12 16.5v.01" />
                    </svg>
                    <div>
                      <b>Couldn&rsquo;t sign you in</b>
                      <span>{serverError}</span>
                    </div>
                  </div>
                )}

                <form onSubmit={signIn} noValidate>
                  <div className="auth-field">
                    <label htmlFor="auth-email">Email</label>
                    <div className="auth-input-wrap">
                      <input
                        id="auth-email" type="email" inputMode="email" autoComplete="username"
                        placeholder="you@yourbusiness.in" required autoFocus
                        value={email} onChange={(ev) => setEmail(ev.target.value)}
                      />
                    </div>
                  </div>

                  <div className="auth-field">
                    <div className="auth-label-row">
                      <label htmlFor="auth-password">Password</label>
                      <Link to="/forgot-password">Forgot password?</Link>
                    </div>
                    <div className="auth-input-wrap">
                      <input
                        id="auth-password" type={reveal ? 'text' : 'password'}
                        autoComplete="current-password" required
                        value={password} onChange={(ev) => setPassword(ev.target.value)}
                        onKeyUp={(ev) => setCaps(ev.getModifierState?.('CapsLock') ?? false)}
                        onBlur={() => setCaps(false)}
                      />
                      <button
                        type="button" className="auth-reveal"
                        aria-label={reveal ? 'Hide password' : 'Show password'}
                        onClick={() => setReveal((v) => !v)}
                      >
                        {reveal ? 'Hide' : 'Show'}
                      </button>
                    </div>
                    {caps && (
                      /* Not role="alert": nothing has gone wrong yet, and
                         interrupting a screen reader mid-word to say so would
                         be worse than the typo it prevents. Polite, so it is
                         read at the next natural pause. */
                      <p className="auth-caps" aria-live="polite">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                          strokeWidth="2.3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                          <path d="m5 12 7-7 7 7" /><path d="M12 19V5" />
                        </svg>
                        Caps Lock is on.
                      </p>
                    )}
                  </div>

                  <button className="auth-cta" type="submit" disabled={busy}>
                    {busy ? 'Signing in…' : 'Sign in'}
                  </button>
                </form>

                <p className="auth-foot">
                  New here? <Link to="/register">Create a business account</Link>
                </p>
              </>
      )}
    </AuthShell>
  );
}
