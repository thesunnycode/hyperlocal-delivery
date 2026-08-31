import type { AuthResult } from '../types/api';

/** "Ravi Kumar" -> "RK", "Nandini" -> "NA". Falls back to the email. */
export function initials(name: string | null | undefined, email: string): string {
  const source = (name ?? '').trim();
  if (!source) return email.slice(0, 2).toUpperCase();
  // noUncheckedIndexedAccess is on, so every index widens with undefined.
  const parts = source.split(/\s+/).filter(Boolean);
  const first = parts[0] ?? '';
  const last = parts[parts.length - 1] ?? '';
  if (parts.length === 1) return first.slice(0, 2).toUpperCase();
  return `${first.charAt(0)}${last.charAt(0)}`.toUpperCase();
}

/**
 * The moment between a successful credential check and the dashboard opening.
 * It names who you signed in as before the whole app changes under you.
 *
 * Deliberately wordless beyond that: no route path (developer-facing) and no
 * explanation of how role routing works (our mechanism, not the user's
 * concern). The name and the role pill are the whole message.
 */
export default function AuthHandoff({ session }: { session: AuthResult }) {
  const { user, role } = session;
  const label = role === 'OWNER' ? 'Business owner' : 'Delivery rider';

  return (
    <div aria-live="polite">
      <h1>Signing you in</h1>

      <div className="auth-route">
        <div className="auth-route-who">
          <div className="auth-avatar auth-pop">{initials(user?.name, user?.email ?? '')}</div>
          <div>
            <b className="auth-rise auth-d1">{user?.name ?? user?.email}</b>
            <span className="auth-rise auth-d2">{user?.email}</span>
            <div className="auth-rolepill auth-rise auth-d3">{label}</div>
          </div>
        </div>
        <div className="auth-bar"><i /></div>
      </div>
    </div>
  );
}
