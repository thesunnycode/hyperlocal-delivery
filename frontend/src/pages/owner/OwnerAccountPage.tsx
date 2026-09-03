import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogOut, Lock, Check } from 'lucide-react';
import { me, updateMe } from '../../api/authApi';
import { useAuth } from '../../lib/AuthContext.tsx';
import { useToast } from '../../lib/ToastContext.tsx';
import { useFatalError } from '../../lib/FatalErrorContext.tsx';
import { initials, isActionableError, cx } from '../../utils/format';
import type { User } from '../../types/api';

/**
 * OD9 — the owner's own account.
 *
 * Refero (Dub 59a2c8a5, Gladia 46a50798, Framer 284ea81b, Fourthwall
 * 432c4c8e): bordered cards, one concern each, with a tinted footer strip
 * carrying that card's action.
 *
 * The four editable fields were four cards with four Save buttons, on Dub's
 * per-field pattern. That reads well for a settings page of independent
 * toggles; for a name, a phone and two business fields it meant four round
 * trips and four buttons to keep track of. They are one card and one Save,
 * which patches only the fields that changed. Email, password and sign-out
 * stay separate cards — those genuinely are separate concerns.
 *
 * This page used to be entirely read-only and told the owner that "the API
 * doesn't yet expose a self-service edit for account details". That was wrong:
 * `PATCH /api/auth/me` has existed all along and simply had no client. Name,
 * phone, business name and business phone are editable here now. Email stays
 * locked because it is the login and the request record has no field for it.
 *
 * ── Redesign ────────────────────────────────────────────────────────────
 * The save bar is sticky while there are unsaved changes. Four fields tall
 * meant that editing the business phone on a laptop viewport put Save below
 * the fold — the owner typed, then had to go hunting for the way to keep it.
 * It pins only when dirty; a bar reading nothing is furniture.
 *
 * `position: sticky`, not `fixed`, so it stays inside its own card and stops
 * pinning when that card scrolls past. A fixed bar would hang over the Email
 * and Sign-out cards below, which it has nothing to do with.
 *
 * The unsaved count gained an amber dot — "2 unsaved changes" in the same grey
 * as every other label on the page is easy to read straight past.
 *
 * On the audit: this page was flagged as having no change-password section
 * while the rider app did. It has had one all along — the card below routes to
 * `/forgot-password`, exactly as `AgentAccountPage` does.
 */

/** field key on the patch, card title, description, locked caveat */
type Editable = {
  key: 'fullName' | 'phone' | 'businessName' | 'businessPhone';
  title: string;
  desc: string;
  of: (u: User) => string;
  type?: string;
};

const CARDS: Editable[] = [
  { key: 'fullName', title: 'Your name', desc: 'Shown to your riders and on the shipments you create.', of: (u) => u.name || '' },
  { key: 'phone', title: 'Your phone', desc: 'Where support reaches you. It is never shown to customers.', of: (u) => u.phone || '', type: 'tel' },
  { key: 'businessName', title: 'Business name', desc: 'The name at the top of this console, on your reports, and on the customer tracking page.', of: (u) => u.businessName || '' },
  { key: 'businessPhone', title: 'Business phone', desc: 'The number a customer is told to call if a delivery goes wrong.', of: (u) => u.businessPhone || '', type: 'tel' }
];

export default function OwnerAccountPage() {
  const { user, logout, setUser } = useAuth();
  const toast = useToast();
  const { reportError } = useFatalError();
  const navigate = useNavigate();
  const [acct, setAcct] = useState<User | null>(user);
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  useEffect(() => {
    me().then((u) => { setAcct(u); setDraft({}); }).catch(reportError);
  }, [reportError]);

  /* One form, one Save. It was four forms with four Save buttons — four
     round trips to change a name and a phone number, and four places to
     wonder which button applied to what. `PATCH /auth/me` takes the whole
     object, so the page sends only the fields that actually changed. */
  const changed = (): Partial<Record<Editable['key'], string>> => {
    if (!acct) return {};
    const out: Partial<Record<Editable['key'], string>> = {};
    for (const c of CARDS) {
      const next = (draft[c.key] ?? c.of(acct)).trim();
      if (next !== c.of(acct)) out[c.key] = next;
    }
    return out;
  };
  const dirtyKeys = Object.keys(changed()) as Editable['key'][];

  const save = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (busy || !acct) return;
    const patch = changed();
    if (Object.keys(patch).length === 0) return;
    setBusy('all');
    try {
      const updated = await updateMe(patch);
      setAcct(updated);
      setUser?.(updated);
      setDraft({});
      setSaved('all');
      setTimeout(() => setSaved(null), 2400);
    } catch (err) {
      const message = err instanceof Error ? err.message : '';
      if (isActionableError(err)) toast(message || 'Could not save that.', 'accent');
      else reportError(err);
    } finally {
      setBusy(null);
    }
  };

  const revert = () => setDraft({});

  const head = (
    <div className="ow-head narrow">
      <div>
        <h1>Account</h1>
        <div className="sub">Your sign-in and business details</div>
      </div>
    </div>
  );

  if (!acct) {
    return (
      <>
        {head}
        <div className="ow-acct">
          <span className="ow-sk ow-sk-avatar-lg" />
          <span className="ow-sk ow-sk-name-lg" />
          <span className="ow-sk ow-sk-card-1" />
          <span className="ow-sk ow-sk-card-2" />
        </div>
      </>
    );
  }

  return (
    <>
      {head}
      <div className="ow-acct">
        <div className="ow-acct-h">
          <span className="av" aria-hidden="true">{initials(acct.name || 'Owner')}</span>
          <div>
            <span className="role">Business owner</span>
            <h2>{acct.name}</h2>
          </div>
        </div>

        <form className="ow-scard" onSubmit={save}>
          <h3>Your details</h3>
          <p className="desc">
            Your name and phone are yours. The business name and number are what your
            riders and your customers see.
          </p>

          {CARDS.map((card) => {
            const current = card.of(acct);
            const value = draft[card.key] ?? current;
            return (
              <div className="ow-acct-f" key={card.key}>
                <label htmlFor={`acct-${card.key}`}>{card.title}</label>
                <input
                  id={`acct-${card.key}`}
                  type={card.type ?? 'text'}
                  value={value}
                  required
                  aria-describedby={`acct-${card.key}-d`}
                  onChange={(e) => setDraft((d) => ({ ...d, [card.key]: e.target.value }))}
                />
                <p className="hint" id={`acct-${card.key}-d`}>{card.desc}</p>
              </div>
            );
          })}

          {/* Sticky only while dirty. A pinned bar that reads nothing is
              furniture, and it would sit over the fields it belongs to. */}
          <div className={cx('foot', dirtyKeys.length > 0 && 'ow-sticky')}>
            <span>
              {saved === 'all'
                ? <span className="ok"><Check size={13} strokeWidth={2.6} /> Saved</span>
                : dirtyKeys.length
                  ? (
                    <span className="ow-dirty">
                      {dirtyKeys.length} unsaved {dirtyKeys.length === 1 ? 'change' : 'changes'}
                    </span>
                  )
                  : ''}
            </span>
            <span className="ow-foot-acts">
              {dirtyKeys.length > 0 && (
                <button type="button" className="ow-linky" onClick={revert}>Discard</button>
              )}
              <button type="submit" className="ow-btn pri"
                disabled={dirtyKeys.length === 0 || busy === 'all'}>
                {busy === 'all' ? 'Saving…' : 'Save changes'}
              </button>
            </span>
          </div>
        </form>

        <section className="ow-scard">
          <h3>Email</h3>
          <p className="desc">This is the address you sign in with.</p>
          <div className="val mono">{acct.email}</div>
          <div className="foot">
            <span>Your email is your login and cannot be changed.</span>
            <Lock size={13} strokeWidth={2.1} aria-hidden="true" />
          </div>
        </section>

        <section className="ow-scard">
          <h3>Password</h3>
          <p className="desc">
            Changing it signs you out everywhere else. You will get a code by email to confirm
            it is you.
          </p>
          <div className="ow-scard-act">
            <button type="button" className="ow-btn" onClick={() => navigate('/forgot-password')}>
              Change password
            </button>
          </div>
        </section>

        <section className="ow-scard danger">
          <h3>Sign out</h3>
          <p className="desc">
            Ends this session on this device. Your riders stay signed in on theirs, and nothing
            about your shipments changes.
          </p>
          <div className="ow-scard-act">
            <button type="button" className="ow-btn danger"
              onClick={() => { logout(); navigate('/login', { replace: true }); }}>
              <LogOut size={15} strokeWidth={2.1} /> Sign out
            </button>
          </div>
        </section>
      </div>
    </>
  );
}
