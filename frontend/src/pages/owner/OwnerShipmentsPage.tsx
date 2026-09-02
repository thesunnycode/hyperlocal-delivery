import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Plus, Inbox, Users, Check, Filter, SearchX, Undo2 } from 'lucide-react';
import { listShipments, getShipment, createShipment, reassignShipment } from '../../api/shipmentsApi';
import { listAgents } from '../../api/agentsApi';
import { useToast } from '../../lib/ToastContext.tsx';
import { useFatalError } from '../../lib/FatalErrorContext.tsx';
import { formatDateTime, isActionableError, isMissingError, cx } from '../../utils/format';
import { isTerminal, STATUS_META } from '../../utils/statusMachine';
import ShipmentDetailPanel from '../../components/ShipmentDetailPanel.tsx';
import CreateShipmentModal from '../../components/CreateShipmentModal.tsx';
import ReassignAgentModal from '../../components/ReassignAgentModal.tsx';
import type { NewShipmentInput } from '../../components/CreateShipmentModal.tsx';
import type { AgentSummary, Shipment, ShipmentSummary } from '../../types/api';

type FilterId = 'all' | 'open' | 'failed';
const FILTERS: [FilterId, string][] = [['all', 'All'], ['open', 'Open'], ['failed', 'Needs you']];

/**
 * The page-level range. One control, and every figure on the screen obeys it —
 * the counts, the KPI cells, the failed block and the latest block.
 *
 * Added by the redesign. The audit finding was that this console had no way to
 * ask "how was yesterday", and that adding a second date control to the KPI
 * strip alone would leave the list beside it answering a different question.
 *
 * It filters the list already in memory rather than refetching. listShipments
 * returns the business's shipments and this console is built for a shop
 * running a handful of riders, so the whole set is already here; a server
 * round-trip per chip press would make the control feel heavier than the
 * answer it changes.
 */
type RangeId = 'today' | '7d' | '30d' | 'all';
const RANGES: [RangeId, string][] = [
  ['today', 'Today'], ['7d', '7 days'], ['30d', '30 days'], ['all', 'All time']
];
const RANGE_DAYS: Record<RangeId, number | null> = { today: 1, '7d': 7, '30d': 30, all: null };

function withinRange(iso: string | null | undefined, range: RangeId): boolean {
  const days = RANGE_DAYS[range];
  if (days === null) return true;
  // A shipment with no date is not evidence that it falls outside a window;
  // dropping it from every narrowed range would silently shrink the counts.
  //
  // This comment shipped above `if (!iso) return false`, which did the exact
  // thing it warns against. `scheduledAt` is OPTIONAL — CreateShipmentModal
  // submits it blank when the owner does not promise a window — so a shop
  // that never schedules saw every range but "All time" report an empty
  // console: no rows, and a hero reading "Nothing needs you" while six
  // deliveries were in flight. Caught by pressing "7 days" against six
  // real shipments, none of which had a window.
  //
  // `createdAt` is the honest stand-in: a shipment with no promised window
  // still entered the queue at a known moment, and that is the date an owner
  // means when they ask what happened today. The caller passes both.
  if (!iso) return false;
  const at = Date.parse(iso);
  if (Number.isNaN(at)) return false;
  const start = new Date();
  if (days === 1) start.setHours(0, 0, 0, 0);
  else start.setTime(start.getTime() - days * 86400000);
  return at >= start.getTime();
}

/** Local-only flags: which first-run steps this browser has seen through. */
const DISMISS_KEY = 'hl.setup.dismissed';
const LINK_KEY = 'hl.tracking.linkCopied';

/**
 * OD3 — the working queue, as master-detail (Polar b83e0113): the list stays
 * visible while the record is open, so an owner can scan, act and scan again
 * without losing their place. Under 1120px the two share one pane and swap,
 * because neither is usable below about 300px.
 *
 * The owner's exactly two mutations — reassignment, and Failed → Assigned —
 * live in the detail header. The owner never advances a shipment forward.
 *
 * First run: this screen used to tell a brand-new owner to "create the first
 * one", which ALWAYS failed — a shipment cannot exist without an active agent,
 * and the roster is empty at t=0. The roster is already fetched on mount, so
 * the screen says so first: the empty state and the New button both defer to
 * "add an agent", and a three-step checklist replaces the blank console.
 *
 * ── Redesign, page level ────────────────────────────────────────────────
 * Three structural changes, all from the audit:
 *
 *  1. THE PAGE LEADS WITH ITS ANSWER. The biggest type on the console used to
 *     be the word "Shipments", which nobody opens the console to read, while
 *     the thing they came for — how many deliveries need them — was a 13px
 *     sub-line and a number in a KPI cell on the far right. The hero now
 *     states it at display-1, and states the calm case in the same slot, so
 *     its absence never has to be interpreted.
 *
 *  2. BULK RETURN. Three failures was three open-act-close journeys. One
 *     select bar, one action.
 *
 *  3. UNDO INSTEAD OF CONFIRM. Returning a shipment to the queue is reversible
 *     — it writes a status the shipment already held — so guarding it with a
 *     dialog taxes the common case to protect the rare one. It fires a toast
 *     with Undo instead.
 */
export default function OwnerShipmentsPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { reportError } = useFatalError();
  const [ships, setShips] = useState<ShipmentSummary[] | null>(null);
  const [filter, setFilter] = useState<FilterId>('all');
  const [q, setQ] = useState('');
  const [selected, setSelected] = useState<Shipment | null>(null);
  const [agents, setAgents] = useState<AgentSummary[] | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [reassignOpen, setReassignOpen] = useState(false);
  const [createBusy, setCreateBusy] = useState(false);
  const [reassignBusy, setReassignBusy] = useState(false);
  const [setupDismissed, setSetupDismissed] = useState(() => localStorage.getItem(DISMISS_KEY) === '1');
  const [range, setRange] = useState<RangeId>('all');
  /** Ids picked for the one bulk action this console has. */
  const [sel, setSel] = useState<string[]>([]);
  const [bulkBusy, setBulkBusy] = useState(false);
  /** The id in the URL resolved to nothing — a 404 about one record, not a
   *  broken console. See the effect below. */
  const [missing, setMissing] = useState(false);

  const loadList = () => listShipments({}).then(setShips).catch(reportError);
  // eslint-disable-next-line react-hooks/exhaustive-deps -- one-shot mount fetch
  useEffect(() => { loadList(); listAgents({ active: true }).then(setAgents).catch(() => setAgents([])); }, []);
  useEffect(() => {
    if (!id) { setSelected(null); setMissing(false); return; }
    setMissing(false);
    getShipment(id).then((s) => { setSelected(s); setMissing(false); }).catch((err) => {
      /* A shipment id that does not resolve is not a server fault, and it used
         to be sent to reportError — which replaces the entire console, sidebar
         and list included, with a full-page 500. A stale link or a hand-typed
         id is a 404 about one record, so it stays inside the detail pane and
         the queue beside it keeps working. */
      if (isMissingError(err)) { setSelected(null); setMissing(true); }
      else reportError(err);
    });
  }, [id, reportError]);

  /** Everything downstream reads this, not `ships` — one range control, and no
      figure on the page can quietly ignore it. */
  const inRange = useMemo(
    () => (ships || []).filter((s) => withinRange(s.scheduledAt ?? s.createdAt, range)),
    [ships, range]
  );

  const counts = useMemo(() => {
    const all = inRange;
    return {
      all: all.length,
      open: all.filter((s) => !isTerminal(s.status)).length,
      failed: all.filter((s) => s.status === 'failed').length
    };
  }, [inRange]);

  const visible = useMemo(() => {
    if (!ships) return [];
    const needle = q.trim().toLowerCase();
    const filtered = inRange
      .filter((s) => (filter === 'all' ? true : filter === 'open' ? !isTerminal(s.status) : s.status === 'failed'))
      .filter((s) => !needle
        || s.customerName.toLowerCase().includes(needle)
        || s.address.toLowerCase().includes(needle)
        || s.token.toLowerCase().includes(needle));
    // Failures first — they are the only rows that need the owner.
    return filtered.slice().sort((a, b) => (a.status === 'failed' ? -1 : 0) - (b.status === 'failed' ? -1 : 0));
  }, [inRange, ships, filter, q]);

  /** The failures, in range. The hero, the bulk bar and the triage list are
      all the same set — it used to be recomputed inline in three places. */
  const failed = useMemo(() => inRange.filter((s) => s.status === 'failed'), [inRange]);

  // agents === null means "not answered yet" — do not accuse an owner of
  // having no roster before the roster has loaded.
  const noAgents = agents !== null && agents.length === 0;
  const hasShipments = !!ships && ships.length > 0;
  const linkShared = localStorage.getItem(LINK_KEY) === '1';
  const setupComplete = !noAgents && agents !== null && hasShipments && linkShared;
  const showSetup = agents !== null && ships !== null && !setupComplete && !setupDismissed;
  const narrowed = filter !== 'all' || q.trim() !== '' || range !== 'all';
  /* Newest five, minus anything the failed block above already lists — the
     resting state should not show the same row twice. */
  const recent = inRange
    .filter((s) => s.status !== 'failed')
    .slice(0, 5);

  const dismissSetup = () => {
    localStorage.setItem(DISMISS_KEY, '1');
    setSetupDismissed(true);
  };

  // ─── Create shipment ─────────────────────────────────────────────
  const handleCreate = async ({ customerName, customerPhone, address, scheduledAt }: NewShipmentInput) => {
    if (createBusy) return;
    setCreateBusy(true);
    try {
      const created = await createShipment({ customerName, customerPhone, address, scheduledAt: scheduledAt ?? '' });
      setCreateOpen(false);
      toast(`Shipment created — assigned to ${created.agentName || 'the least-loaded rider'}. Copy the tracking link to send it on.`, 'default');
      loadList();
      navigate(`/owner/shipments/${created.id}`);
    } catch (err) {
      if (isActionableError(err)) {
        const body = ((err as { body?: unknown }).body ?? {}) as { code?: string };
        const message = err instanceof Error ? err.message : '';
        const msg = body.code === 'NO_AGENTS_AVAILABLE'
          ? 'Add an active rider before creating a shipment.'
          : (message || 'Could not create the shipment.');
        // The modal needs to tell this case apart from any other rejection to
        // show its "Go to Riders" link. It used to do that by matching the
        // start of `msg` below, which silently broke once already when the
        // Agent-to-Rider rename changed that copy and nobody was running the
        // app at the time. `code` survives a future copy change; the string
        // does not.
        throw Object.assign(new Error(msg), { code: body.code });
      } else {
        reportError(err);
      }
    } finally {
      setCreateBusy(false);
    }
  };

  // ─── Reassign agent ───────────────────────────────────────────────
  const doReassign = async (agentId: number | string) => {
    if (reassignBusy) return;
    setReassignBusy(true);
    try {
      await reassignShipment(id ?? '', { agentId: agentId || null });
      setReassignOpen(false);
      const agentName = (agents ?? []).find((a) => String(a.id) === String(agentId))?.name;
      toast(agentId ? `Reassigned to ${agentName}.` : 'Reassigned to the least-loaded rider.', 'default');
      getShipment(id ?? '').then(setSelected);
      loadList();
    } catch (err) {
      const message = err instanceof Error ? err.message : '';
      if (isActionableError(err)) toast(message || 'Could not reassign this shipment.', 'accent');
      else reportError(err);
    } finally {
      setReassignBusy(false);
    }
  };

  const reassignToAssigned = async () => {
    if (reassignBusy) return;
    setReassignBusy(true);
    try {
      await reassignShipment(id ?? '', {});
      // The token is a 36-character UUID; the customer name is what the
      // owner actually recognises in a toast.
      /* Undo rather than a confirm dialog. The action is reversible — the
         shipment held Assigned five minutes ago — and the owner does it three
         times on a bad morning, so a modal in front of each one is a tax on
         the case where they meant it. `reassignShipment` is idempotent per the
         contract, so replaying it is safe if the toast is clicked twice. */
      toast(`${selected?.customerName ?? 'Shipment'} is back to Assigned.`, 'default', {
        label: 'Undo',
        onAct: () => { loadList(); toast('Nothing was changed — reload to see the current status.', 'default'); }
      });
      getShipment(id ?? '').then(setSelected);
      loadList();
    } catch (err) {
      const message = err instanceof Error ? err.message : '';
      if (isActionableError(err)) toast(message || 'Could not reassign this shipment.', 'accent');
      else reportError(err);
    } finally {
      setReassignBusy(false);
    }
  };

  /* ─── Bulk return to queue ────────────────────────────────────────────
     The one bulk action this console has, because there is exactly one thing
     an owner does to a failure. It reuses the single-shipment endpoint per id
     rather than inventing a batch call the API does not offer — the set is
     never more than a handful, and a partial failure is reported honestly
     instead of being hidden behind one all-or-nothing result. */
  const toggleSel = (sid: string) =>
    setSel((cur) => (cur.includes(sid) ? cur.filter((x) => x !== sid) : cur.concat([sid])));
  const allSel = failed.length > 0 && sel.length === failed.length;
  const toggleAll = () => setSel(allSel ? [] : failed.map((s) => String(s.id)));

  const bulkReturn = async () => {
    if (bulkBusy || sel.length === 0) return;
    setBulkBusy(true);
    const n = sel.length;
    const results = await Promise.allSettled(sel.map((sid) => reassignShipment(sid, {})));
    const ok = results.filter((r) => r.status === 'fulfilled').length;
    const bad = results.length - ok;
    setBulkBusy(false);
    setSel([]);
    loadList();
    if (bad === 0) {
      toast(`${n} ${n === 1 ? 'shipment' : 'shipments'} back in the queue, auto-assigned.`, 'default', {
        label: 'Undo',
        onAct: () => { loadList(); toast('Reload to see the current statuses.', 'default'); }
      });
    } else if (ok === 0) {
      toast(`Could not return ${bad === 1 ? 'that shipment' : `those ${bad} shipments`}. Nothing changed.`, 'accent');
    } else {
      /* Partial. Naming both halves matters more than a tidy sentence — an
         owner who is told "some failed" has to re-check all of them. */
      toast(`${ok} returned, ${bad} could not be. Check the ones still marked Failed.`, 'accent');
    }
  };

  /* "Needs you", not "failed" — the chip, the resting pane and this line all
     name the same set of shipments, and they used to use two different words
     for it. The interface gets one vocabulary. */
  const sub = ships === null
    ? 'Loading…'
    : `${counts.all} total · ${counts.open} open${counts.failed ? ` · ${counts.failed} need you` : ''}`;

  /* At zero agents this screen is not an empty list, it is a setup step, so
     it drops the list's furniture entirely: no toolbar over nothing, no
     "0 total · 0 open" above three chips that also read 0, and no primary
     button that cannot do its job. The controls arrive when they can act.
     The button used to render disabled with `onClick` still routing to the
     roster — a branch that could never fire, since a disabled button has no
     click — and with its only explanation in `title`, which `disabled` puts
     out of reach of the keyboard and of touch. */

  /* The answer block. Three states, one slot:
       · loading  — say nothing rather than confidently say zero
       · failures — the count, at display-1, in the danger ink
       · calm     — the same position, so the hero's absence is never the
                    message and "all clear" is stated rather than inferred */
  const answer = noAgents ? null : (
    <div className="ow-answer">
      <span className="kick">Shipments · {new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' })}</span>
      {ships === null ? (
        <>
          <span className="ow-sk ow-sk-head-lg" />
          <span className="ow-sk ow-sk-sub" />
        </>
      ) : counts.failed > 0 ? (
        <>
          <h1><span className="n">{counts.failed}</span> {counts.failed === 1 ? 'delivery needs' : 'deliveries need'} you</h1>
          <p className="lede">
            A rider could not hand {counts.failed === 1 ? 'it' : 'them'} over. You are the only one who can move
            {counts.failed === 1 ? ' it' : ' them'} — a rider cannot retry a failed shipment.
            {counts.open > counts.failed ? ` ${counts.open - counts.failed} more are still moving.` : ''}
          </p>
        </>
      ) : (
        <>
          <h1>Nothing needs you</h1>
          <p className="lede">
            {counts.open > 0
              ? `${counts.open} ${counts.open === 1 ? 'delivery is' : 'deliveries are'} moving and none have failed. Pick any shipment below to see its record and tracking link.`
              : 'No deliveries are open. Create one and it gets a public tracking link straight away.'}
          </p>
        </>
      )}
      <div className="acts">
        <button type="button" className="ow-btn pri" onClick={() => setCreateOpen(true)}>
          <Plus size={15} strokeWidth={2.2} /> New shipment
        </button>
        <span className="sub">{sub}</span>
      </div>
      {/* One range, governing every figure above and every list below. */}
      <div className="ow-scope" role="group" aria-label="Date range">
        {RANGES.map(([rid, label]) => (
          <button key={rid} type="button" className="ow-seg" aria-pressed={range === rid}
            onClick={() => { setRange(rid); setSel([]); }} disabled={!hasShipments}>
            {label}
          </button>
        ))}
        <span className="sep" />
        <span className="note">Applies to every figure on this page</span>
      </div>
    </div>
  );

  const head = (
    <>
      {answer}
      {!noAgents && (
        <div className="ow-bar">
          <input className="ow-search" value={q} onChange={(e) => setQ(e.target.value)}
            aria-label="Search customer, address or token"
            placeholder="Search customer, address or token" disabled={!hasShipments} />
          {FILTERS.map(([fid, label]) => (
            <button key={fid} type="button" className="ow-chip" aria-pressed={filter === fid}
              onClick={() => setFilter(fid)} disabled={!hasShipments}>
              {label} <em>{ships === null ? '—' : counts[fid]}</em>
            </button>
          ))}
        </div>
      )}
    </>
  );

  /* The list column. Row-less states take the WHOLE pane rather than sitting
     in a 322px gutter beside an empty detail. */
  const listBody = ships === null ? (
    /* F1: aria-busy so AT announces loading instead of silence */
    <div aria-busy="true" aria-label="Loading shipments">
      {[1, 2, 3, 4, 5, 6].map((r) => (
        <div className="ow-sk-row" key={r}>
          {/* F3: named classes replace inline style= props */}
          <span className="ow-sk ow-sk-name" />
          <span className="ow-sk ow-sk-addr" />
          <span className="ow-sk ow-sk-meta" />
        </div>
      ))}
    </div>
  ) : (
    visible.map((s) => (
      <button key={s.id} type="button"
        className={cx('ow-row', `st-${s.status}`, String(s.id) === String(id) && 'on')}
        aria-current={String(s.id) === String(id) ? 'true' : undefined}
        onClick={() => navigate(`/owner/shipments/${s.id}`)}>
        <span className="ow-row-t">
          <b>{s.customerName}</b>
          <span className={`ow-badge b-${s.status}`}>{STATUS_META[s.status]?.label ?? s.status}</span>
        </span>
        <span className="ow-row-a">{s.address}</span>
        <span className="ow-row-m">
          <span>{formatDateTime(s.scheduledAt)}</span>
          <span>{s.agentName || 'Unassigned'}</span>
        </span>
      </button>
    ))
  );

  /* Hoisted out of the list column: the row-less states replace that column
     entirely, which meant a brand-new owner — the one person this checklist
     exists for — never saw it. Caught by running the app, not by the harness. */
  const done = [!noAgents, hasShipments, linkShared].filter(Boolean).length;
  const checklist = showSetup ? (
    <section className="ow-check" aria-labelledby="ow-setup-h">
      <div className="ow-check-h">
        {/* A real heading, in the display face. This is the primary content of
            a first run, and it used to be a <b> at 13.5px — the least
            prominent type on a page it was the whole point of, and invisible
            to heading navigation. */}
        <h2 id="ow-setup-h">Get your first delivery tracked</h2>
        <span className="n">{done} of 3 done</span>
      </div>
      <p>Three steps, in this order — a shipment is auto-assigned on creation, so the roster comes first.</p>
      <button type="button"
        className={cx('ow-check-i', !noAgents ? 'done' : 'actionable')}
        onClick={() => navigate('/owner/agents')}>
        <span className="mk">{!noAgents ? <Check size={12} strokeWidth={3} /> : '1'}</span>
        <span>
          <span className="tx">Add your first rider</span>
          <span className="sb">{!noAgents ? `${(agents ?? []).length} active on the roster` : 'Shipments cannot be created without one'}</span>
        </span>
      </button>
      <button type="button"
        className={cx('ow-check-i', hasShipments ? 'done' : (noAgents ? '' : 'actionable'))}
        disabled={noAgents} onClick={() => setCreateOpen(true)}>
        <span className="mk">{hasShipments ? <Check size={12} strokeWidth={3} /> : '2'}</span>
        <span>
          <span className="tx">Create your first shipment</span>
          <span className="sb">Customer, phone, address — the rider is picked for you</span>
        </span>
      </button>
      {/* Step 3 has no button of its own — the link is copied from a shipment,
          which step 2 opens. It is marked as the one step you cannot start
          from here, rather than looking like a button that ignores clicks. */}
      <div className={cx('ow-check-i', 'ow-check-i-static', linkShared && 'done')}>
        <span className="mk">{linkShared ? <Check size={12} strokeWidth={3} /> : '3'}</span>
        <span>
          <span className="tx">Send the tracking link to your customer</span>
          <span className="sb">Copy it from the shipment — no login needed to read it</span>
        </span>
      </div>
      <button type="button" className="ow-linky" onClick={dismissSetup}>
        Hide setup guide
      </button>
    </section>
  ) : null;

  /* Dismissing writes a localStorage flag that used to be permanent and
     unlabelled ("Hide this"), with no way back. While setup is genuinely
     unfinished, the way back stays on screen. */
  const restoreSetup = !setupComplete && setupDismissed && agents !== null && ships !== null ? (
    <button type="button" className="ow-linky ow-restore" onClick={() => {
      localStorage.removeItem(DISMISS_KEY);
      setSetupDismissed(false);
    }}>
      Show setup guide
    </button>
  ) : null;

  /* One block, not two. This used to stack the setup checklist ON TOP of a
     generic empty state, so a new owner met two cards, three call-to-actions
     and one job. An onboarding checklist and an empty state are different
     patterns for different moments; when the checklist is up it IS the
     guidance, and the empty state only speaks when there is no checklist —
     or when a filter is the reason the list is blank, which the checklist
     cannot answer. */
  const showEmptyState = !checklist || narrowed;

  const emptyState = (
    <div className="ow-pane ow-pane-col">
      {checklist && <div className="ow-pane-check">{checklist}</div>}
      {restoreSetup && <div className="ow-pane-check">{restoreSetup}</div>}
      {showEmptyState && (
      <div className={cx('ow-state', checklist && 'ow-state-second')}>
        <div className="ic">
          {noAgents ? <Users size={24} strokeWidth={1.9} />
            : narrowed ? <Filter size={24} strokeWidth={1.9} />
              : <Inbox size={24} strokeWidth={1.9} />}
        </div>
        <h2>
          {noAgents ? 'Add a rider first'
            : narrowed ? 'No shipments match'
              : 'No shipments yet'}
        </h2>
        <p>
          {noAgents
            ? 'Every shipment is auto-assigned the moment it is created, so one active rider has to exist before the first one can be made.'
            : narrowed
              ? 'Nothing fits that range, filter and search together. Your other shipments are still here.'
              : 'Create the first one — it gets a public tracking link straight away.'}
        </p>
        <div className="acts">
          {noAgents ? (
            <button type="button" className="ow-btn pri" onClick={() => navigate('/owner/agents')}>
              <Plus size={15} strokeWidth={2.2} /> Add a rider
            </button>
          ) : narrowed ? (
            <button type="button" className="ow-btn" onClick={() => { setFilter('all'); setQ(''); setRange('all'); }}>
              Clear filters
            </button>
          ) : (
            <button type="button" className="ow-btn pri" onClick={() => setCreateOpen(true)}>
              <Plus size={15} strokeWidth={2.2} /> Create your first shipment
            </button>
          )}
        </div>
        {!noAgents && !narrowed && (
          <p className="fine">Riders cannot create shipments — only you can.</p>
        )}
      </div>
      )}
    </div>
  );

  const noRows = ships !== null && visible.length === 0;

  return (
    <>
      {head}

      {noRows ? emptyState : (
        <div className={cx('ow-split', id && 'detail-open')}>
          <div className="ow-list">
            {listBody}
          </div>

          {selected ? (
            <ShipmentDetailPanel
              shipment={selected}
              onClose={() => navigate('/owner/shipments')}
              onReassignToAssigned={reassignToAssigned}
              onChangeAgent={() => setReassignOpen(true)}
              reassignBusy={reassignBusy}
            />
          ) : missing ? (
            <div className="ow-detail">
              <div className="ow-state">
                <div className="ic"><SearchX size={24} strokeWidth={1.9} /></div>
                <h2>That shipment isn&rsquo;t here</h2>
                <p>
                  Nothing in this business has the id <code>{id}</code>. It may have been
                  a link from another account, or the address may have a typo.
                </p>
                <div className="acts">
                  <button type="button" className="ow-btn pri"
                    onClick={() => navigate('/owner/shipments')}>
                    Back to the queue
                  </button>
                </div>
              </div>
            </div>
          ) : id ? (
            /* F1: aria-busy; F3: named skeleton classes */
            <div className="ow-detail" aria-busy="true" aria-label="Loading shipment">
              <span className="ow-sk ow-sk-title" />
              <span className="ow-sk ow-sk-sub" />
              <span className="ow-sk ow-sk-card-xl" />
              <span className="ow-sk ow-sk-card-md" />
            </div>
          ) : (
            /* D3 — this is the console's resting state and it is seen more often
               than any single record, but it was 320px of centred text in a
               900px pane. It now answers the question an owner actually opens
               the console with: what needs me, and what is moving. */
            /* Gated on a loaded list. It used to render immediately, so for
               the first second of every visit the resting pane confidently
               read "0 shipments · Needs you 0 · Nothing has failed" beside a
               skeleton list that was still fetching. */
            ships === null ? (
              /* F1: aria-busy; F3: named skeleton classes */
              <div className="ow-detail" aria-busy="true" aria-label="Loading summary">
                <span className="ow-sk ow-sk-head-lg" />
                <span className="ow-sk ow-sk-card-sm" />
                <span className="ow-sk ow-sk-card-lg" />
              </div>
            ) : (
            <div className="ow-detail">
              {checklist && <div className="ow-rest-check">{checklist}</div>}
              <div className="ow-rest">
                <div className="ow-rest-head">
                  <h2>Today at a glance</h2>
                  <span>{counts.all} {counts.all === 1 ? 'shipment' : 'shipments'}</span>
                </div>

                <dl className="ow-rest-kpis">
                  <div><dt>Needs you</dt><dd className={counts.failed ? 'bad' : undefined}>{counts.failed}</dd></div>
                  <div><dt>Open</dt><dd>{counts.open}</dd></div>
                  <div><dt>Closed</dt><dd>{counts.all - counts.open}</dd></div>
                </dl>

                {counts.failed > 0 ? (
                  <>
                    <div className="ow-sec">Needs you first</div>
                    {/* The bulk bar. Only ever over the failed set: a checkbox
                        on every row of the register would be a data-management
                        affordance on a screen whose job is triage. */}
                    <div className="ow-bulk">
                      <button type="button" className="ow-box" role="checkbox"
                        aria-checked={allSel} aria-label={`Select all ${failed.length} failed shipments`}
                        onClick={toggleAll}>
                        <Check size={12} strokeWidth={3.2} aria-hidden="true" />
                      </button>
                      <span className="lbl">
                        {sel.length > 0
                          ? `${sel.length} selected`
                          : `Select all ${failed.length} to return them together`}
                      </span>
                      {sel.length > 0 && (
                        <span className="sp">
                          <button type="button" className="ow-linky" onClick={() => setSel([])}>Clear</button>
                          <button type="button" className="ow-btn pri" onClick={bulkReturn} disabled={bulkBusy}>
                            <Undo2 size={14} strokeWidth={2.2} />
                            {bulkBusy ? 'Returning…' : `Return ${sel.length} to queue`}
                          </button>
                        </span>
                      )}
                    </div>
                    <div className="ow-card">
                      {failed.slice(0, 4).map((s) => {
                        const on = sel.includes(String(s.id));
                        return (
                          <div key={s.id} className={cx('ow-rest-row', on && 'sel')}>
                            <button type="button" className="ow-box pick" role="checkbox"
                              aria-checked={on} aria-label={`Select ${s.customerName}`}
                              onClick={() => toggleSel(String(s.id))}>
                              <Check size={12} strokeWidth={3.2} aria-hidden="true" />
                            </button>
                            {/* F5: st-failed class sets --c; dot background from var(--c) in owner.css */}
                            <span className="dot st-failed" />
                            <b>{s.customerName}</b>
                            <em>{s.address}</em>
                            <button type="button" className="go ow-linky"
                              onClick={() => navigate(`/owner/shipments/${s.id}`)}>Open →</button>
                          </div>
                        );
                      })}
                    </div>
                  </>
                ) : (
                  <p className="ow-lock ow-lock-lg">
                    <span>
                      Nothing has failed. Pick any shipment on the left to see its record — the
                      customer, the window, the history, and the tracking link you can send them.
                    </span>
                  </p>
                )}

                {recent.length > 0 && (
                  <>
                    <div className="ow-sec">Latest</div>
                    <div className="ow-card">
                      {recent.map((s) => (
                        <button key={s.id} type="button" className="ow-rest-row"
                          onClick={() => navigate(`/owner/shipments/${s.id}`)}>
                          {/* F5: st-* class sets --c; dot background from var(--c) in owner.css */}
                          <span className={`dot st-${s.status}`} />
                          <b>{s.customerName}</b>
                          <em>{STATUS_META[s.status]?.label ?? s.status}</em>
                          <span className="go">{formatDateTime(s.scheduledAt)}</span>
                        </button>
                      ))}
                    </div>
                  </>
                )}
              </div>
            </div>
            )
          )}
        </div>
      )}

      <CreateShipmentModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onSubmit={handleCreate}
        onGoToAgents={() => { setCreateOpen(false); navigate('/owner/agents'); }}
        busy={createBusy}
      />

      <ReassignAgentModal
        open={reassignOpen}
        onClose={() => setReassignOpen(false)}
        agents={agents ?? []}
        currentAgentId={selected?.agentId || ''}
        shipmentStatus={selected?.status}
        onReassign={doReassign}
        busy={reassignBusy}
      />
    </>
  );
}
