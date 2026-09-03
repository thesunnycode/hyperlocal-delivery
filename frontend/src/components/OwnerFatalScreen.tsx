import { useNavigate } from 'react-router-dom';
import { TimerOff } from 'lucide-react';
import { useFatalError } from '../lib/FatalErrorContext.tsx';
import { useAuth } from '../lib/AuthContext.tsx';
import EdgeStateCard from './EdgeStateCard.tsx';

/**
 * Full-screen replacement for the app shell — used by OwnerLayout and
 * AdminLayout when FatalErrorContext has something to show.
 *
 * Two different events, deliberately shaped differently: an expired session is
 * a neutral pause and keeps the icon tile, because "session" is not a status
 * code anyone would quote. A real failure is a 500 and prints its code plus a
 * support-searchable reference (Lemon Squeezy 4887bb77, Descript e0f5a98e).
 *
 * ── Redesign ────────────────────────────────────────────────────────────
 * The 500 now prints the support contact. Telling someone to quote a reference
 * code without saying who to quote it TO is a dead end dressed as a next step.
 *
 * The session screen deliberately does not: an expired session is fixed by
 * signing in again, and offering a support number for it invites a call
 * nobody needs to take.
 */
export default function OwnerFatalScreen() {
  const { error, clearError } = useFatalError();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  if (!error) return null;

  if (error.kind === 'session') {
    return (
      <EdgeStateCard
        variant="screen"
        icon={TimerOff}
        brand={user?.businessName}
        title="Your session expired"
        body="You were signed out after a period of inactivity. Nothing has been lost — your shipments and any changes are saved exactly as they were."
        actions={[{
          // Was /owner/login, which no longer exists as a screen — it only
          // redirects. Send them straight to the one sign-in page.
          label: 'Sign in again',
          onClick: () => { logout(); clearError(); navigate('/login', { replace: true }); }
        }]}
      />
    );
  }

  return (
    <EdgeStateCard
      variant="screen"
      tone="accent"
      code="500"
      brand={user?.businessName}
      reference={error.reference}
      support
      title="Something went wrong on our end"
      body={error.reference
        ? 'This isn’t something you did — refreshing usually fixes it. If it keeps happening, share the reference code with support.'
        : 'This isn’t something you did — refreshing usually fixes it. If it keeps happening, try again in a moment.'}
      actions={[
        { label: 'Retry', onClick: () => { clearError(); window.location.reload(); } },
        { label: 'Back to shipments', onClick: () => { clearError(); navigate('/owner/shipments'); } }
      ]}
      foot="This screen replaces the console, not the product. Your shipments and everything your riders logged are untouched."
    />
  );
}
