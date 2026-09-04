import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ChevronLeft, Phone, X, Lock, ArrowRight, AlertTriangle, Undo2, Navigation } from 'lucide-react';
import {
  getShipment, confirmPickup, startTransit, markOutForDelivery,
  markDelivered, logFailedAttempt, markReturned
} from '../../api/shipmentsApi';
import { useToast } from '../../lib/ToastContext.tsx';
import { STATUS_META, AGENT_NEXT, FAILURE_REASONS, FLOW, isTerminal } from '../../utils/statusMachine';
import { telHref, mapsHref, formatDateTime, formatTime, shortToken } from '../../utils/format';
import ConfirmDialog from '../../components/ConfirmDialog.tsx';
import { useModalBehaviour } from '../../lib/useModalBehaviour';
import type { FailureReason, Shipment, ShipmentStatus } from '../../types/api';

/**
 * A4 — the only mutator. Facts, then immutable history, then the dock.
 *
 * The dock is sticky so the one legal move is always on screen: this page is
 * used one-handed, at a door, in daylight, and the whole point of the screen
 * must never be a scroll away.
 *
 * Two gates are preserved from the original and are not cosmetic:
 *   - Mark delivered and Returned sit behind a confirm dialog. Both are
 *     terminal with no undo.
 *   - Failed opens the attempt sheet first. The status changes only once a
 *     reason exists, so a mis-tap cannot strand a shipment in `failed`.
 *
 * Sheet layout follows Klarna's report sheet (Refero 6b5715ab): rows divided
 * by hairlines with the indicator on the RIGHT, so the row is the tap target
 * and the dot lands under the thumb.
 */

type ConfirmSpec = { title: string; body: string; confirmLabel: string; tone?: 'default' | 'danger'; run: () => void | Promise<void> };

type AdvanceFn = (id: number | string, note?: string | null) => Promise<Shipment>;

const ADVANCE_FN: Partial<Record<ShipmentStatus, AdvanceFn>> = {
  assigned: confirmPickup,
  picked_up: startTransit,
  in_transit: markOutForDelivery,
  out_for_delivery: markDelivered
};

/** Plain-language headline per state — the badge already carries the label. */
const HEADLINE: Record<ShipmentStatus, string> = {
  assigned: 'Ready for pickup',
  picked_up: 'Picked up',
  in_transit: 'Moving to the area',
  out_for_delivery: 'On the final leg',
  delivered: 'Delivered',
  failed: 'Attempt failed',
  returned: 'Returned to sender'
};

export default function AgentShipmentDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [shipment, setShipment] = useState<Shipment | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [reason, setReason] = useState<FailureReason | null>(null);
  const [sheetNote, setSheetNote] = useState('');
  const [sheetError, setSheetError] = useState(false);
  const [confirm, setConfirm] = useState<ConfirmSpec | null>(null);
  const [attemptBusy, setAttemptBusy] = useState(false);
  const [note, setNote] = useState('');
  const [noteOpen, setNoteOpen] = useState(false);
  const sheetPanel = useModalBehaviour(sheetOpen, () => setSheetOpen(false));

  const load = () => getShipment(id ?? '').then(setShipment).catch(() => navigate('/agent/assignments'));
  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- re-fetch only when the URL id changes
  }, [id]);

  if (!shipment) {
    return (
      <div className="ag"><div className="ag-wrap">
        <div className="ag-bar">
          <span className="ag-skel ag-skel-avatar" />
          <span className="ag-skel ag-skel-title" />
        </div>
        <div className="ag-pad">
          <div className="ag-card ag-hero">
            <span className="ag-skel ag-skel-block ag-skel-hero-tag" />
            <span className="ag-skel ag-skel-block ag-skel-hero-h" />
            <div className="ag-flow">{FLOW.map((f) => <i key={f} />)}</div>
          </div>
          <div className="ag-card ag-facts">
            {[1, 2, 3].map((r) => (
              <div key={r} className="ag-fact">
                <span className="ag-skel ag-skel-fact-label" />
                <span className="ag-skel ag-skel-fact-value" />
              </div>
            ))}
          </div>
        </div>
      </div></div>
    );
  }

  const cur = STATUS_META[shipment.status];
  const step = AGENT_NEXT[shipment.status];
  const terminal = isTerminal(shipment.status) || shipment.status === 'failed';

  const doAdvance = async (n: string) => {
    const advance = ADVANCE_FN[shipment.status];
    // Both exist for exactly the states whose dock offers a forward move,
    // which is the only way to reach this. Guards for the compiler.
    if (!advance || !step) return;
    await advance(id ?? '', n);
    toast(`Marked ${STATUS_META[step.next].label.toLowerCase()}.`, 'default');
    setNote(''); setNoteOpen(false);
    load();
  };

  const onAdvance = () => {
    if (step?.kind === 'OUTCOME') {
      setConfirm({
        title: 'Mark this delivered?',
        body: 'Delivered is terminal — there is no further move after this.',
        confirmLabel: 'Mark delivered',
        run: () => doAdvance(note)
      });
    } else {
      doAdvance(note);
    }
  };

  const onMarkReturned = () => {
    setConfirm({
      title: 'Mark as returned?',
      body: 'Returned is terminal — there is no further move after this. Only the owner can act on it from here.',
      confirmLabel: 'Returned',
      tone: 'danger',
      run: async () => { await markReturned(id ?? '', note); toast('Marked returned.', 'accent'); load(); }
    });
  };

  const submitAttempt = async () => {
    if (!reason) { setSheetError(true); return; }
    if (attemptBusy) return;
    setAttemptBusy(true);
    try {
      await logFailedAttempt(id ?? '', { reason, notes: sheetNote.trim() });
      setSheetOpen(false); setReason(null); setSheetNote(''); setSheetError(false);
      toast('Attempt logged — marked failed.', 'accent');
      load();
    } finally {
      setAttemptBusy(false);
    }
  };

  let terminalNote = '';
  if (shipment.status === 'delivered') terminalNote = `Closed ${formatDateTime(shipment.deliveredAt)}. Read-only from here.`;
  if (shipment.status === 'failed') terminalNote = 'Attempt logged. Only the owner can reassign this back to Assigned.';
  if (shipment.status === 'returned') terminalNote = 'Returned to the business. Read-only from here.';

  // failed and returned branch off step 4 — neither ever reached step 5, so
  // the last segment must stay unlit even though `returned` is terminal.
  const branched = shipment.status === 'failed' || shipment.status === 'returned';
  const reached = branched ? 4 : cur.step;

  return (
    <div className="ag"><div className="ag-wrap">
      <div className="ag-bar">
        <button type="button" className="ag-iconbtn" aria-label="Back to assignments"
          onClick={() => navigate('/agent/assignments')}>
          <ChevronLeft size={19} strokeWidth={2.4} />
        </button>
        <div>
          <div className="ag-bar-tok" title={shipment.token}>{shortToken(shipment.token)}</div>
          <div className="ag-bar-name">{shipment.customerName}</div>
        </div>
      </div>

      <div className="ag-pad">
        <div className={`ag-card ag-hero ag-s-${shipment.status}`}>
          <div className="ag-hero-top">
            <span className={`ag-badge ag-s-${shipment.status}`}>{cur.label}</span>
            <span className="ag-step">
              {branched ? 'Branched at step 4' : `Step ${cur.step} of ${FLOW.length}`}
            </span>
          </div>
          <h1 className="ag-hero-h">{HEADLINE[shipment.status]}</h1>
          <div className="ag-flow" aria-label={`Step ${reached} of ${FLOW.length}`}>
            {FLOW.map((f, i) => {
              const n = i + 1;
              const cls = n < reached ? 'done' : n === reached ? (shipment.status === 'delivered' ? 'done' : 'now') : '';
              return <i key={f} className={cls} />;
            })}
          </div>
        </div>

        <dl className="ag-card ag-facts">
          <div className="ag-fact">
            <dt>Address</dt>
            {/* The rider's actual job is finding this door, and until now the
                address was plain text they had to retype into another app.
                The universal Maps URL hands off to whatever the phone has
                installed rather than pinning Google. */}
            <dd>
              <a className="ag-addr" href={mapsHref(shipment.address)}
                target="_blank" rel="noopener noreferrer">
                {shipment.address}
                <span className="ag-addr-go">
                  <Navigation size={13} strokeWidth={2.3} aria-hidden="true" />
                  Directions
                </span>
              </a>
            </dd>
          </div>
          <div className="ag-fact">
            <dt>Phone</dt>
            <dd className="ag-mono">
              <a href={telHref(shipment.customerPhone)}>{shipment.customerPhone}</a>
            </dd>
          </div>
          <div className="ag-fact">
            <dt>Scheduled</dt>
            <dd className="ag-mono">{formatDateTime(shipment.scheduledAt)}</dd>
          </div>
          <div className="ag-fact">
            {/* "0 of 3" never said what happens at 3. A rider deciding
                whether to try the doorbell one more time is exactly who needs
                to know the cap sends the parcel back. */}
            <dt>Attempts</dt>
            <dd>
              <span className="ag-mono">{(shipment.attempts || []).length} of 3</span>
              <span className="ag-fact-sub">After the third, it goes back to the store.</span>
            </dd>
          </div>
        </dl>

        <a className="ag-secondary" href={telHref(shipment.customerPhone)}>
          <Phone size={17} strokeWidth={2.1} /> Call {shipment.customerName?.split(' ')[0] ?? 'customer'}
        </a>

        <div className="ag-sec">Event log</div>
        <ul className="ag-log">
          {(shipment.events || []).map((e, i) => (
            <li key={i} className={`ag-s-${e.status}`}>
              <span className="t">{formatTime(e.stamp ?? e.createdAt)}</span>
              <span className="m" />
              <span><b>{e.label ?? STATUS_META[e.status]?.label}</b><em>{e.meta ?? e.changedBy ?? ''}</em></span>
            </li>
          ))}
        </ul>

        {(shipment.attempts || []).length > 0 && (
          <>
            <div className="ag-sec">Attempt log</div>
            <ul className="ag-log">
              {shipment.attempts.map((a) => (
                <li key={a.id} className="ag-s-failed">
                  <span className="t">{formatTime(a.stamp ?? a.attemptedAt)}</span>
                  <span className="m" />
                  <span>
                    <b>Attempt {a.no ?? a.attemptNumber} · {a.reason ?? a.failureReason}</b>
                    <em>{a.note || 'No note'}</em>
                  </span>
                </li>
              ))}
            </ul>
          </>
        )}
      </div>

      <div className={`ag-dock ag-s-${shipment.status}`}>
        {terminal || !step ? (
          <>
            <div className="ag-dock-lbl"><i />No moves left</div>
            <div className="ag-locked">
              <Lock size={16} strokeWidth={2.1} />
              <div><b>{cur.label}</b>{terminalNote}</div>
            </div>
          </>
        ) : (
          <>
            <div className="ag-dock-lbl">
              {/* Was "One move, two outcomes" over a dock with three
                  buttons. It is a question with three answers. */}
              <i />{step.fork ? 'How did it go?' : 'Next step'}
            </div>

            {noteOpen ? (
              <textarea className="ag-note" value={note} autoFocus
                placeholder="Optional note for your owner"
                onChange={(e) => setNote(e.target.value)} />
            ) : (
              <button type="button" className="ag-note-btn" onClick={() => setNoteOpen(true)}>
                Add a note
              </button>
            )}

            <button type="button" className={`ag-cta${step.kind === 'OUTCOME' ? ' ag-outcome' : ''}`}
              onClick={onAdvance}>
              {step.cta} <ArrowRight size={17} strokeWidth={2.2} />
            </button>

            {step.fork && (
              <div className="ag-fork">
                <button type="button" className="ag-danger"
                  onClick={() => { setSheetNote((s) => s || note); setSheetOpen(true); }}>
                  <AlertTriangle size={16} strokeWidth={2.1} /> Log failed attempt
                </button>
                <button type="button" onClick={onMarkReturned}>
                  <Undo2 size={16} strokeWidth={2.1} /> Return
                </button>
              </div>
            )}
          </>
        )}
      </div>

      {sheetOpen && (
        <>
          <div className="ag-scrim" onClick={() => setSheetOpen(false)} />
          <div ref={sheetPanel} role="dialog" aria-modal="true" aria-label="Log delivery attempt" className="ag-sheet">
            <div className="ag-grab" />
            <div className="ag-sheet-h">
              <b>Why did it fail?</b>
              <button type="button" className="ag-sheet-x" aria-label="Close" data-modal-close
                onClick={() => setSheetOpen(false)}>
                <X size={18} strokeWidth={2.3} />
              </button>
            </div>

            {sheetError && (
              <p className="ag-sheet-err" role="alert">Pick a reason — the status cannot change without one.</p>
            )}

            <div role="radiogroup" aria-label="Failure reason">
              {FAILURE_REASONS.map((r) => (
                <button key={r} type="button" role="radio" aria-checked={reason === r} className="ag-opt"
                  onClick={() => { setReason(r); setSheetError(false); }}>
                  <span>{r}</span><span className="ag-dot" />
                </button>
              ))}
            </div>

            <div className="ag-sheet-note">
              <label htmlFor="attempt-note">Note for your owner <span>(optional)</span></label>
              <textarea id="attempt-note" className="ag-note ag-note-sm"
                placeholder="Knocked twice, no response"
                value={sheetNote} onChange={(e) => setSheetNote(e.target.value)} />
            </div>

            <div className="ag-sheet-cta">
              <button type="button" onClick={submitAttempt} disabled={attemptBusy}>
                {attemptBusy ? 'Logging…' : 'Log attempt'}
              </button>
            </div>
            <p className="ag-sheet-foot">
              This becomes attempt {(shipment.attempts || []).length + 1} of 3. Appended to the log, and cannot be edited.
            </p>
          </div>
        </>
      )}

      {/* open is !!confirm, so confirm?.run is present whenever onConfirm
          fires; the optional calls are for the compiler. */}
      <ConfirmDialog
        open={!!confirm}
        title={confirm?.title} body={confirm?.body} confirmLabel={confirm?.confirmLabel}
        tone={confirm?.tone}
        onCancel={() => setConfirm(null)}
        onConfirm={async () => { const run = confirm?.run; setConfirm(null); await run?.(); }}
      />
    </div></div>
  );
}
