import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronLeft, LogOut, KeyRound, ChevronRight, Pencil, X } from 'lucide-react';
import { me, updateMe } from '../../api/authApi';
import { useAuth } from '../../lib/AuthContext.tsx';
import { useToast } from '../../lib/ToastContext.tsx';
import { formatDate, isActionableError } from '../../utils/format';
import ConfirmDialog from '../../components/ConfirmDialog.tsx';
import { useModalBehaviour } from '../../lib/useModalBehaviour';
import type { User } from '../../types/api';

/**
 * A6 — the agent's own account.
 *
 * Owners had /owner/account from the start; agents had nothing, so the only
 * account control an agent could reach was a sign-out button, and the redesign
 * had put that on an avatar that signed them out on a single tap. A mis-tap at
 * a doorstep should not end the session.
 *
 * Name and phone are the agent's OWN to change: `PATCH /api/auth/me` accepts
 * both for any authenticated user (AuthService only gates businessName and
 * businessPhone on the owner role). This page used to say "ask them to change
 * your name, phone or email" — true only of the email, and only because the
 * request record has no field for it. Email, business and role stay read-only
 * and the copy now says exactly that.
 *
 * Refreshes from /auth/me rather than trusting the cached session, so a change
 * the owner made shows up without a re-login. The cached user is rendered
 * immediately and replaced when the call lands, so the page never blocks.
 */
export default function AgentAccountPage() {
  const { user, logout, setUser } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [fresh, setFresh] = useState<User | null>(null);
  const [confirmOut, setConfirmOut] = useState(false);
  const [editing, setEditing] = useState(false);
  const editPanel = useModalBehaviour(editing, () => setEditing(false));
  const [form, setForm] = useState({ fullName: '', phone: '' });
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    // A stale cache is not worth an error state — the cached user still renders.
    me().then(setFresh).catch(() => {});
  }, []);

  const acct = fresh ?? user;

  const rows: [string, string | null | undefined][] = [
    ['Name', acct?.name],
    ['Phone', acct?.phone],
    ['Email', acct?.email],
    ['Business', acct?.businessName],
    ['Business phone', acct?.businessPhone],
    ['Joined', acct?.createdAt ? formatDate(acct.createdAt) : null]
  ];

  const openEdit = () => {
    setForm({ fullName: acct?.name ?? '', phone: acct?.phone ?? '' });
    setEditing(true);
  };

  const saveEdit = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const updated = await updateMe({ fullName: form.fullName.trim(), phone: form.phone.trim() });
      setFresh(updated);
      setUser(updated);
      setEditing(false);
      toast('Details saved.', 'default');
    } catch (err) {
      const message = err instanceof Error ? err.message : '';
      toast(isActionableError(err) ? (message || 'Could not save that.') : 'Could not save that.', 'accent');
    } finally {
      setBusy(false);
    }
  };

  /** "Ravi Kumar" -> "RK". Guarded for noUncheckedIndexedAccess. */
  const parts = (acct?.name ?? '').trim().split(/\s+/).filter(Boolean);
  const first = parts[0] ?? '';
  const last = parts[parts.length - 1] ?? '';
  const badge = !first
    ? 'AG'
    : parts.length === 1
      ? first.slice(0, 2).toUpperCase()
      : `${first.charAt(0)}${last.charAt(0)}`.toUpperCase();

  return (
    <div className="ag"><div className="ag-wrap">
      <div className="ag-bar">
        <button type="button" className="ag-iconbtn" aria-label="Back to assignments"
          onClick={() => navigate('/agent/assignments')}>
          <ChevronLeft size={19} strokeWidth={2.4} />
        </button>
        {/* An h1, not a div: this was the only screen in the app with no
            page heading for a screen reader to land on. */}
        <h1 className="ag-bar-name">Account</h1>
      </div>

      <div className="ag-pad">
        <div className="ag-card ag-acct-hero">
          <div className="ag-avatar ag-avatar-lg">
            {badge}
          </div>
          <div className="ag-acct-name">
            {acct?.name || 'Rider'}
          </div>
          <div className="ag-rolepill">Delivery rider</div>
        </div>

        <dl className="ag-card ag-facts ag-acct-facts">
          {rows.map(([label, value]) => (
            <div className="ag-fact" key={label}>
              <dt>{label}</dt>
              <dd>{value || '—'}</dd>
            </div>
          ))}
        </dl>

        <p className="ag-hint">
          Your name and phone are yours to change. Your email, and the business you deliver
          for, are set by {acct?.businessName || 'your business'}.
        </p>

        <button type="button" className="ag-secondary" onClick={openEdit}>
          <Pencil size={17} strokeWidth={2.1} /> Edit name and phone
          <ChevronRight size={16} strokeWidth={2.2} className="ag-chev-end" />
        </button>

        <button type="button" className="ag-secondary" onClick={() => navigate('/forgot-password')}>
          <KeyRound size={17} strokeWidth={2.1} /> Change password
          <ChevronRight size={16} strokeWidth={2.2} className="ag-chev-end" />
        </button>

        <button type="button" className="ag-secondary ag-signout" onClick={() => setConfirmOut(true)}>
          <LogOut size={17} strokeWidth={2.1} /> Sign out
        </button>
      </div>

      {editing && (
        <div className="cd" role="presentation" onClick={() => setEditing(false)}>
          {/* useModalBehaviour like every other overlay. This one was the
              exception: no Escape, no focus trap, no focus restored to the
              button that opened it. */}
          <div ref={editPanel} className="cd-panel" role="dialog" aria-modal="true"
            aria-labelledby="ag-edit-title" onClick={(e) => e.stopPropagation()}>
            <div className="cd-head">
              <h2 className="cd-title" id="ag-edit-title">Your details</h2>
              <button type="button" className="cd-x" aria-label="Close" data-modal-close
                onClick={() => setEditing(false)}>
                <X size={16} strokeWidth={2.2} />
              </button>
            </div>
            <form onSubmit={(e) => { e.preventDefault(); saveEdit(); }} className="cd-form">
              <div className="cd-field">
                <label htmlFor="ag-name">Name</label>
                <input id="ag-name" value={form.fullName} required minLength={2}
                  onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))} />
              </div>
              <div className="cd-field">
                <label htmlFor="ag-phone">Phone</label>
                <input id="ag-phone" type="tel" value={form.phone} required
                  onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))} />
                <p className="hint">Your owner sees this. Customers never do.</p>
              </div>
              <div className="cd-acts">
                <button type="button" className="cd-btn" onClick={() => setEditing(false)}>Cancel</button>
                <button type="submit" className="cd-btn pri" disabled={busy}>
                  {busy ? 'Saving…' : 'Save'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={confirmOut}
        title="Sign out?"
        body="Your open deliveries stay assigned to you. You will need your password to get back in."
        confirmLabel="Sign out"
        tone="danger"
        onCancel={() => setConfirmOut(false)}
        onConfirm={() => { logout(); navigate('/login', { replace: true }); }}
      />
    </div></div>
  );
}
