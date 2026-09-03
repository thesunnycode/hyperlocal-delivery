import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Plus, Power, Pencil, X, Users, MoreVertical, Eye, Send
} from 'lucide-react';
import {
  listAgents, getAgent, createAgent, updateAgent, deactivateAgent, reactivateAgent
} from '../../api/agentsApi';
import { useAuth } from '../../lib/AuthContext.tsx';
import { useToast } from '../../lib/ToastContext.tsx';
import { useFatalError } from '../../lib/FatalErrorContext.tsx';
import { formatDate, initials, isActionableError } from '../../utils/format';
import ConfirmDialog from '../../components/ConfirmDialog.tsx';
import AgentInviteSheet from '../../components/AgentInviteSheet.tsx';
import { useModalBehaviour } from '../../lib/useModalBehaviour';
import type { Agent, AgentSummary } from '../../types/api';

/**
 * OD7 — the roster.
 *
 * Refero: Fibery 0cc2d799, Boords b325db3c and Reown 9a9ca95d all build people
 * management the same way — a row per person, status inline, and the
 * destructive action behind an OVERFLOW MENU rather than sitting in the row
 * waiting to be mis-clicked. That is the change here: deactivate used to be a
 * permanently visible red button in a detail pane.
 *
 * The invite link moved INTO the add sheet. Cohere 3f80baf1, Apollo 6b408824,
 * Slite c5579f72 and Slab 99b203a3 all show the shareable link inside the
 * invite form, not only in a confirmation afterwards — the owner needs it at
 * the moment they are thinking about the person, and a toast evaporates.
 *
 * Rows show OPEN only. The mockup drew three figures per row, but
 * `GET /agents` returns `openCount` alone; delivered and failed come from
 * `GET /agents/:id`, so they live in the detail sheet rather than costing one
 * request per row.
 *
 * ANSWERED, by running it: POST /api/agents does NOT email anything.
 * AgentService generates a random password, stores only the hash, and its own
 * comment says the agent is expected to use the forgot-password flow. So a
 * newly created agent had no way to discover their own account existed.
 * AgentInviteSheet closes that: it opens automatically after a create, and is
 * available from any row's menu, because an invite that can only be sent once
 * is an invite that gets lost. A server-side invite endpoint would still be
 * better — this makes the owner the delivery channel — but it needs a backend
 * change, and this works today.
 *
 * ── Redesign ────────────────────────────────────────────────────────────
 * Two audit findings against this screen were both wrong, and are recorded
 * rather than silently dropped:
 *
 *   · "The row menu is invisible until hover." It is not — `.ow-kebab` is
 *     always painted and only changes background on hover, which is the
 *     correct treatment for a per-row overflow control.
 *   · "'Never signed in' should offer Resend inline." There is no field to
 *     detect it with. `AgentSummary` carries no `lastLoginAt`, and `joinedAt`
 *     says when the account was made, not whether anyone has used it.
 *     Inferring it from zero counts would label a brand-new active rider as
 *     never having signed in. "Send sign-in link" stays in the row menu,
 *     where it works for every agent, until the API can tell the two apart.
 *
 * What was real: presentation living in the component. Six inline `style=`
 * objects, including a detail-sheet avatar that duplicated `.av` at a larger
 * size with two `var()` lookups inline. All moved to `scopes.css`.
 */
export default function OwnerAgentsPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { user } = useAuth();
  const { reportError } = useFatalError();
  const [agents, setAgents] = useState<AgentSummary[] | null>(null);
  const [filter, setFilter] = useState<'active' | 'all'>('active');
  const [q, setQ] = useState('');
  const [detail, setDetail] = useState<Agent | null>(null);
  const [sheet, setSheet] = useState<'add' | 'edit' | null>(null);
  const [form, setForm] = useState({ name: '', email: '', phone: '' });
  const [confirmToggle, setConfirmToggle] = useState(false);
  const [formBusy, setFormBusy] = useState(false);
  const [invite, setInvite] = useState<{ id: number; name: string; email: string; phone: string | null } | null>(null);
  const [menuFor, setMenuFor] = useState<number | null>(null);
  const sheetRef = useModalBehaviour(sheet !== null, () => setSheet(null));
  const detailRef = useModalBehaviour(!!id && sheet === null, () => navigate('/owner/agents'));
  const menuRef = useRef<HTMLDivElement | null>(null);

  const load = () => listAgents({}).then(setAgents).catch(reportError);
  // eslint-disable-next-line react-hooks/exhaustive-deps -- one-shot mount fetch
  useEffect(() => { load(); }, []);
  useEffect(() => {
    if (id) getAgent(id).then(setDetail).catch(reportError);
    else setDetail(null);
  }, [id, reportError]);

  // A menu that outlives the click that opened it is a menu you cannot close.
  useEffect(() => {
    if (menuFor === null) return;
    const away = (e: MouseEvent) => {
      if (!menuRef.current?.contains(e.target as Node)) setMenuFor(null);
    };
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') setMenuFor(null); };
    document.addEventListener('mousedown', away);
    document.addEventListener('keydown', esc);
    return () => {
      document.removeEventListener('mousedown', away);
      document.removeEventListener('keydown', esc);
    };
  }, [menuFor]);

  const needle = q.trim().toLowerCase();
  const visible = (agents || [])
    .filter((a) => (filter === 'all' ? true : a.active))
    .filter((a) => !needle
      || a.name.toLowerCase().includes(needle)
      || a.email.toLowerCase().includes(needle));
  const activeCount = (agents || []).filter((a) => a.active).length;

  /* The server refuses to deactivate an agent still carrying open shipments.
     When that is the case the dialog's job is to send the owner where the
     block can be cleared, not to offer the call that will bounce. */
  const blockedByOpenWork = !!detail?.active && (detail.openCount ?? 0) > 0;

  const openAdd = () => { setForm({ name: '', email: '', phone: '' }); setSheet('add'); };
  const openEdit = (a: AgentSummary | Agent) => {
    setForm({ name: a.name ?? '', email: a.email ?? '', phone: a.phone ?? '' });
    navigate(`/owner/agents/${a.id}`);
    setSheet('edit');
  };

  const submitForm = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (formBusy) return;
    setFormBusy(true);
    try {
      if (sheet === 'add') {
        const created = await createAgent(form);
        toast('Rider added.', 'default');
        // Straight into the invite: the account is useless until they can
        // reach it, so this is one step of the same job, not a follow-up.
        setInvite({
          id: created.id,
          name: created.name || form.name,
          email: created.email || form.email,
          phone: created.phone ?? form.phone
        });
      } else {
        await updateAgent(id ?? '', { name: form.name, phone: form.phone });
        toast('Rider details saved.', 'default');
      }
      setSheet(null);
      load();
      if (id) getAgent(id).then(setDetail);
    } catch (err) {
      const message = err instanceof Error ? err.message : '';
      if (isActionableError(err)) toast(message || 'Could not save this rider.', 'accent');
      else reportError(err);
    } finally { setFormBusy(false); }
  };

  const doToggle = async () => {
    setConfirmToggle(false);
    if (!detail) return;
    try {
      if (detail.active) { await deactivateAgent(id ?? ''); toast(`${detail.name} deactivated.`, 'accent'); }
      else { await reactivateAgent(id ?? ''); toast(`${detail.name} reactivated.`, 'default'); }
      load();
      getAgent(id ?? '').then(setDetail);
    } catch (err) {
      const message = err instanceof Error ? err.message : '';
      if (isActionableError(err)) toast(message || 'Could not update this rider.', 'accent');
      else reportError(err);
    }
  };

  const head = (
    <>
      <div className="ow-head">
        <div>
          <h1>Riders</h1>
          <div className="sub">
            {agents === null
              ? 'Loading…'
              : `${activeCount} active of ${agents.length}. You create their accounts — they cannot sign up.`}
          </div>
        </div>
        <button type="button" className="ow-btn pri" onClick={openAdd}>
          <Plus size={15} strokeWidth={2.2} /> Add rider
        </button>
      </div>
      <div className="ow-bar">
        <input className="ow-search" value={q} onChange={(e) => setQ(e.target.value)}
          aria-label="Search riders" placeholder="Search riders" disabled={!agents?.length} />
        <button type="button" className="ow-chip" aria-pressed={filter === 'active'}
          onClick={() => setFilter('active')}>
          Active <em>{agents === null ? '—' : activeCount}</em>
        </button>
        <button type="button" className="ow-chip" aria-pressed={filter === 'all'}
          onClick={() => setFilter('all')}>
          All <em>{agents === null ? '—' : agents.length}</em>
        </button>
      </div>
    </>
  );

  return (
    <>
      {head}

      {agents === null ? (
        <div className="ow-people">
          {[1, 2, 3].map((r) => (
            <div className="ow-person" key={r}>
              <span className="ow-sk ow-sk-av" />
              <span className="who2">
                <span className="ow-sk ow-sk-nm" />
                <span className="ow-sk ow-sk-em" />
              </span>
            </div>
          ))}
        </div>
      ) : visible.length === 0 ? (
        <div className="ow-pane">
          <div className="ow-state">
            <div className="ic"><Users size={24} strokeWidth={1.9} /></div>
            <h2>
              {agents.length === 0 ? 'Add someone to deliver'
                : needle ? 'No rider matches'
                  : 'No active riders'}
            </h2>
            <p>
              {agents.length === 0
                ? 'Your roster is empty. A rider has to exist before a shipment can be created, because every shipment is auto-assigned the moment it is made.'
                : needle
                  ? 'No name or email on the roster matches that search.'
                  : 'Every rider here is deactivated, so auto-assign has nobody to pick.'}
            </p>
            <div className="acts">
              {needle
                ? <button type="button" className="ow-btn" onClick={() => setQ('')}>Clear search</button>
                : <button type="button" className="ow-btn pri" onClick={openAdd}>
                  <Plus size={15} strokeWidth={2.2} /> Add rider
                </button>}
              {!needle && agents.length > 0 && filter === 'active' && (
                <button type="button" className="ow-btn" onClick={() => setFilter('all')}>
                  Show deactivated
                </button>
              )}
            </div>
            {agents.length === 0 && (
              <p className="fine">Until a rider exists, new shipments have nobody to auto-assign to.</p>
            )}
          </div>
        </div>
      ) : (
        <div className="ow-people">
          {visible.map((a) => (
            <div className="ow-person" key={a.id}>
              <span className="av" aria-hidden="true">{initials(a.name)}</span>
              <button type="button" className="who2"
                onClick={() => navigate(`/owner/agents/${a.id}`)}>
                <b>{a.name}</b><span>{a.email}</span>
              </button>
              {/* The row was a name, an email and one number in 1080px. Phone
                  is already on AgentSummary and was going unused, and a rider
                  who has never signed in looked identical to one with 200
                  deliveries behind them — which is exactly the row an owner
                  needs to notice. */}
              <span className="ow-person-ph">{a.phone || '—'}</span>
              <span className="ow-sp" />
              <span className={`ow-pill ${a.active ? 'ok' : 'off'}`}>
                {a.active ? 'Active' : 'Deactivated'}
              </span>
              <dl className="ow-load">
                <div><dt>Open</dt><dd>{a.openCount ?? 0}</dd></div>
              </dl>
              {/* Promoted out of the overflow. Nothing is emailed automatically
                  — the account exists but the rider cannot discover it until
                  the owner sends this — so the one action that unblocks a new
                  rider should not be two clicks behind a kebab. Active riders
                  only: a deactivated account has nothing to sign in to. */}
              {a.active && (
                <button type="button" className="ow-send"
                  aria-label={`Send ${a.name} their sign-in link`}
                  onClick={() => setInvite({ id: a.id, name: a.name, email: a.email, phone: a.phone })}>
                  <Send size={13} strokeWidth={2.2} aria-hidden="true" />
                  <span>Send link</span>
                </button>
              )}
              <button type="button" className="ow-kebab" aria-label={`Actions for ${a.name}`}
                aria-haspopup="menu" aria-expanded={menuFor === a.id}
                onClick={() => setMenuFor(menuFor === a.id ? null : a.id)}>
                <MoreVertical size={16} strokeWidth={2.2} />
              </button>

              {menuFor === a.id && (
                <div className="ow-menu" role="menu" ref={menuRef}>
                  <button type="button" role="menuitem"
                    onClick={() => { setMenuFor(null); navigate(`/owner/agents/${a.id}`); }}>
                    <Eye size={14} strokeWidth={2.1} /> View details
                  </button>
                  <button type="button" role="menuitem"
                    onClick={() => { setMenuFor(null); setInvite({ id: a.id, name: a.name, email: a.email, phone: a.phone }); }}>
                    <Send size={14} strokeWidth={2.1} /> Send sign-in link
                  </button>
                  <button type="button" role="menuitem"
                    onClick={() => { setMenuFor(null); openEdit(a); }}>
                    <Pencil size={14} strokeWidth={2.1} /> Edit name and phone
                  </button>
                  <button type="button" role="menuitem" className="danger"
                    onClick={() => {
                      setMenuFor(null);
                      navigate(`/owner/agents/${a.id}`);
                      getAgent(String(a.id)).then((d) => { setDetail(d); setConfirmToggle(true); }).catch(reportError);
                    }}>
                    <Power size={14} strokeWidth={2.1} /> {a.active ? 'Deactivate' : 'Reactivate'}
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* ── detail sheet ─────────────────────────────────────────── */}
      {id && sheet === null && (
        <div className="ow-scrim" role="presentation" onClick={() => navigate('/owner/agents')}>
          <aside ref={detailRef} className="ow-sheet" role="dialog" aria-modal="true"
            aria-label="Rider details" onClick={(e) => e.stopPropagation()}>
            <div className="ow-sheet-h">
              <b>{detail?.name || 'Rider'}</b>
              <button type="button" className="ow-x" data-modal-close aria-label="Close"
                onClick={() => navigate('/owner/agents')}><X size={16} strokeWidth={2.2} /></button>
            </div>
            <div className="ow-sheet-b">
              {!detail ? (
                <>
                  <span className="ow-sk ow-sk-line" />
                  <span className="ow-sk ow-sk-block" />
                </>
              ) : (
                <>
                  <div className="ow-sheet-id">
                    <span className="av" aria-hidden="true">{initials(detail.name)}</span>
                    <span className={`ow-pill ${detail.active ? 'ok' : 'off'}`}>
                      {detail.active ? 'Active' : 'Deactivated'}
                    </span>
                  </div>

                  <div className="ow-sec">Details</div>
                  <dl className="ow-card ow-dl">
                    <dt>Email · login</dt><dd className="mono">{detail.email}</dd>
                    <dt>Phone</dt><dd>{detail.phone || '—'}</dd>
                    {/* formatDate, not toLocaleDateString: the raw call
                        renders 09/06/2026, which is ambiguous and matches no
                        other date in the app. */}
                    <dt>Joined</dt><dd>{formatDate(detail.joinedAt)}</dd>
                  </dl>

                  <div className="ow-sec">Load</div>
                  <dl className="ow-card ow-dl">
                    <dt>Open now</dt><dd>{detail.openCount ?? 0}</dd>
                    <dt>Delivered</dt><dd>{detail.deliveredCount ?? 0}</dd>
                    <dt>Failed</dt><dd>{detail.failedCount ?? 0}</dd>
                  </dl>
                </>
              )}
            </div>
            {detail && (
              <div className="ow-sheet-f">
                <button type="button" className="ow-btn" onClick={() => openEdit(detail)}>
                  <Pencil size={14} strokeWidth={2.1} /> Edit
                </button>
                <button type="button" className="ow-btn danger" onClick={() => setConfirmToggle(true)}>
                  <Power size={14} strokeWidth={2.1} /> {detail.active ? 'Deactivate' : 'Reactivate'}
                </button>
              </div>
            )}
          </aside>
        </div>
      )}

      {/* ── add / edit sheet ─────────────────────────────────────── */}
      {sheet && (
        <div className="ow-scrim" role="presentation" onClick={() => setSheet(null)}>
          <aside ref={sheetRef} className="ow-sheet" role="dialog" aria-modal="true"
            aria-labelledby="ow-agent-title" onClick={(e) => e.stopPropagation()}>
            <div className="ow-sheet-h">
              <b id="ow-agent-title">{sheet === 'add' ? 'Add rider' : 'Edit rider'}</b>
              <button type="button" className="ow-x" data-modal-close aria-label="Close"
                onClick={() => setSheet(null)}><X size={16} strokeWidth={2.2} /></button>
            </div>
            <form id="ow-agent-form" onSubmit={submitForm} className="ow-sheet-b">
              <div className="ow-f">
                <label htmlFor="ag-name">Name</label>
                <input id="ag-name" value={form.name} required
                  onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
              </div>
              {sheet === 'add' ? (
                <div className="ow-f">
                  <label htmlFor="ag-email">Email</label>
                  <input id="ag-email" type="email" value={form.email} required
                    onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))} />
                  <p className="hint">This is their login and cannot be changed later. Nothing is emailed to it — they set their own password with &ldquo;Forgot password&rdquo;.</p>
                </div>
              ) : (
                <div className="ow-f">
                  <label htmlFor="ag-email">Email · locked</label>
                  <input id="ag-email" value={form.email} disabled />
                  <p className="hint">The email is the login, so it is fixed once the account exists.</p>
                </div>
              )}
              <div className="ow-f">
                <label htmlFor="ag-phone">Phone</label>
                <input id="ag-phone" type="tel" value={form.phone} required
                  onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))} />
              </div>

              {sheet === 'add' && (
                <p className="hint ow-hint-tight">
                  Nothing is emailed to them. As soon as you save, you will get a message to
                  send so they can set their own password.
                </p>
              )}
            </form>
            <div className="ow-sheet-f">
              <button type="submit" form="ow-agent-form" className="ow-btn pri grow" disabled={formBusy}>
                {formBusy ? 'Saving…' : (sheet === 'add' ? 'Add rider' : 'Save changes')}
              </button>
              <button type="button" className="ow-linky" onClick={() => setSheet(null)}>Cancel</button>
            </div>
          </aside>
        </div>
      )}

      {/* The open-shipment count belongs IN the confirmation, not beside it.
          The server REFUSES to deactivate an agent who still carries open
          shipments ("Rider has N active shipment(s); reassign before
          deactivating"), so when there are any this dialog says the attempt
          will be rejected rather than promising a soft delete that cannot
          happen — verified by running it. */}
      <AgentInviteSheet
        open={!!invite}
        agentId={invite?.id ?? null}
        onClose={() => setInvite(null)}
        name={invite?.name ?? ''}
        email={invite?.email ?? ''}
        phone={invite?.phone ?? null}
        businessName={user?.businessName}
      />

      <ConfirmDialog
        open={confirmToggle}
        title={detail?.active ? `Deactivate ${detail?.name}?` : `Reactivate ${detail?.name}?`}
        body={!detail?.active
          ? 'They become eligible for auto-assign again immediately.'
          : (detail.openCount ?? 0) > 0
            ? `${detail.name} is still carrying ${detail.openCount} open ${(detail.openCount ?? 0) === 1 ? 'shipment' : 'shipments'}. Deactivation is refused until those are reassigned — do that from the Shipments queue first, then come back.`
            : 'A soft delete — history and past shipments stay intact, but auto-assign stops picking them up until reactivated.'}
        confirmLabel={detail?.active
          ? ((detail.openCount ?? 0) > 0 ? 'Reassign their shipments' : 'Deactivate')
          : 'Reactivate'}
        tone={detail?.active && (detail.openCount ?? 0) === 0 ? 'danger' : 'default'}
        onCancel={() => setConfirmToggle(false)}
        /* When the server will refuse, the button does the thing that
           unblocks it instead of the thing that fails. Offering "Try anyway"
           for an outcome the dialog has just described as refused is a dead
           end dressed as a choice. */
        onConfirm={blockedByOpenWork
          ? () => { setConfirmToggle(false); navigate('/owner/shipments'); }
          : doToggle}
      />
    </>
  );
}
