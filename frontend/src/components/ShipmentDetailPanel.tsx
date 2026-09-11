import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Copy, Check, ExternalLink, ArrowLeft, Lock, UserCog, Undo2, X, XCircle } from 'lucide-react';
import { formatDateTime, formatTime } from '../utils/format';
import { STATUS_META, isTerminal, canReassignAgent, canRestoreToAssigned, canCancel } from '../utils/statusMachine';
import type { Shipment } from '../types/api';

/**
 * The right-hand record in the owner's master-detail (Polar b83e0113): the
 * customer, the window, the tracking link, the attempt log and the history.
 *
 * The public tracking link is what the product promises at signup, and it used
 * to appear only as a truncated mono token. It is a first-class row: the full
 * URL, a copy action, and a way to see exactly what the customer sees. Copying
 * it satisfies step 3 of the first-run checklist.
 *
 * Owner actions sit at the top, beside the status, rather than in a band
 * halfway down — they are two buttons, and the state machine gives the owner
 * no others.
 */
export default function ShipmentDetailPanel({
  shipment,
  onClose,
  onReassignToAssigned,
  onChangeAgent,
  reassignBusy,
  onCancelShipment,
  cancelBusy
}: {
  shipment: Shipment | null;
  /** Deselect and go back to the resting "Today at a glance" pane. Without
   *  this the only way out of a record was picking a different one. */
  onClose?: () => void;
  onReassignToAssigned?: () => void;
  onChangeAgent?: () => void;
  reassignBusy?: boolean;
  /** Opens the confirm dialog — cancelling is terminal and irreversible,
   *  unlike the two mutations above, so it is not fired directly. */
  onCancelShipment?: () => void;
  cancelBusy?: boolean;
}) {
  const navigate = useNavigate();
  const [copied, setCopied] = useState(false);

  if (!shipment) return null;

  const trackUrl = `${window.location.origin}/track/${shipment.token}`;
  const meta = STATUS_META[shipment.status];
  const canChangeAgent = canReassignAgent(shipment.status);
  const canRestore = canRestoreToAssigned(shipment.status);
  const canCancelShipment = canCancel(shipment.status);
  const attempts = shipment.attempts || [];
  const events = shipment.events || [];

  /* Attempts and status events interleaved into one list, oldest first. A
     failed attempt already produces a status event at the same instant, so
     the event is dropped in favour of the attempt row, which is the one that
     carries the reason and the agent's note. */
  const attemptStamps = new Set(
    attempts.map((a) => a.stamp ?? a.attemptedAt).filter(Boolean)
  );
  const merged = [
    ...events
      .filter((e) => !(e.status === 'failed' && attemptStamps.size > 0))
      .map((e, i) => ({
        /* The backend's ShipmentEventDto has no id field, so there is no
           genuinely unique value to key on — two events of the same status
           landing in the same second (e.g. an auto-assign immediately
           followed by a reassign) produce identical stamp+status pairs.
           Confirmed live: reassigning a shipment right after creation does
           exactly this. The index is included unconditionally, not just as
           a null fallback, so two colliding events still get distinct keys —
           safe here because this array is never reordered after the initial
           chronological sort. */
        key: `e-${i}-${e.stamp ?? e.createdAt ?? 'unknown'}-${e.status}`,
        at: e.stamp ?? e.createdAt,
        sort: new Date(e.stamp ?? e.createdAt ?? 0).getTime(),
        colour: `var(--st-${e.status})`,
        title: e.label || STATUS_META[e.status]?.label || e.status,
        detail: [e.meta || e.changedBy, e.notes && `“${e.notes}”`]
          .filter(Boolean).join(' — ')
      })),
    ...attempts.map((a) => ({
      key: `a${a.id}`,
      at: a.stamp ?? a.attemptedAt,
      sort: new Date(a.stamp ?? a.attemptedAt ?? 0).getTime(),
      colour: 'var(--st-failed)',
      title: `Attempt ${a.no ?? a.attemptNumber} failed · ${a.reason || a.failureReason || 'No reason given'}`,
      detail: [a.agentName || 'Rider', a.note && `“${a.note}”`]
        .filter(Boolean).join(' — ')
    }))
  ].sort((x, y) => x.sort - y.sort);

  const copyLink = async () => {
    try {
      // F1: await the write so we only mark success when the clipboard
      // actually received the value. On failure, fall back to window.prompt
      // so the owner can copy manually rather than seeing a false "Copied".
      await navigator.clipboard.writeText(trackUrl);
      localStorage.setItem('hl.tracking.linkCopied', '1');
      setCopied(true);
      setTimeout(() => setCopied(false), 1600);
    } catch {
      // Clipboard API unavailable (HTTP, permissions denied, old browser).
      // prompt pre-selects the text so the owner can Ctrl+C / long-press copy.
      window.prompt('Copy this link:', trackUrl);
    }
  };

  return (
    <div className="ow-detail">
      <div className="ow-backrow">
        <button type="button" className="ow-btn" onClick={() => navigate('/owner/shipments')}>
          <ArrowLeft size={14} strokeWidth={2.2} /> All shipments
        </button>
      </div>

      <div className="ow-dhead">
        {/* The status belongs with the name it describes. It used to sit at
            the end of the button row, at button size and weight — a red
            "Failed" pill inline with two real buttons reads as a third one. */}
        {/* F4: ow-dhead-title replaces style={{ minWidth: 0 }} */}
        <div className="ow-dhead-title">
          <div className="ow-dtitle">
            <h2>{shipment.customerName}</h2>
            <span className={`ow-badge lg b-${shipment.status}`}>{meta?.label ?? shipment.status}</span>
          </div>
          <div className="tok">{shipment.token}</div>
        </div>
        {/* F4: ow-dhead-acts replaces style={{ display:'flex', gap:8, ... }} */}
        <div className="ow-dhead-acts">
          {/* Both of these used to be called "Reassign", side by side, doing
              different things. One puts a failed shipment back in the queue;
              the other swaps who is carrying it. */}
          {canRestore && (
            <button type="button" className="ow-btn pri" onClick={onReassignToAssigned} disabled={reassignBusy}>
              <Undo2 size={14} strokeWidth={2.1} /> {reassignBusy ? 'Returning…' : 'Return to queue'}
            </button>
          )}
          {canChangeAgent && (
            <button type="button" className="ow-btn" onClick={onChangeAgent}>
              <UserCog size={14} strokeWidth={2.1} /> Change rider
            </button>
          )}
          {canCancelShipment && (
            <button type="button" className="ow-btn danger" onClick={onCancelShipment} disabled={cancelBusy}>
              <XCircle size={14} strokeWidth={2.1} /> Cancel shipment
            </button>
          )}
          {onClose && (
            /* F3: "Back to shipment list" — describes the navigation outcome,
               not the visual X metaphor which could be read as "close/terminate
               the shipment". */
            <button type="button" className="ow-x ow-dclose" aria-label="Back to shipment list"
              onClick={onClose}>
              <X size={16} strokeWidth={2.2} />
            </button>
          )}
        </div>
      </div>

      {!canChangeAgent && (
        /* F4: ow-lock-top replaces style={{ marginTop: 14 }} */
        <p className="ow-lock ow-lock-top">
          <Lock size={14} strokeWidth={2.1} />
          {/* F4: "terminal" replaced with plain language — "terminal" is
              state-machine jargon an owner may not recognise. */}
          <span>
            This shipment is closed — nothing more to change. The owner never
            advances a shipment forward; only the rider moves it.
          </span>
        </p>
      )}

      <div className="ow-sec">Details</div>
      <dl className="ow-card ow-dl">
        <dt>Address</dt><dd>{shipment.address}</dd>
        <dt>Customer phone</dt><dd className="mono">{shipment.customerPhone || '—'}</dd>
        <dt>Rider</dt><dd>{shipment.agentName || 'Unassigned'}</dd>
        <dt>Scheduled</dt><dd>{formatDateTime(shipment.scheduledAt)}</dd>
        <dt>Delivered</dt><dd>{shipment.deliveredAt ? formatDateTime(shipment.deliveredAt) : '—'}</dd>
        <dt>Created</dt><dd>{formatDateTime(shipment.createdAt)}</dd>
      </dl>

      <div className="ow-sec">Customer tracking link</div>
      <div className="ow-link">
        <code>{trackUrl}</code>
        <button type="button" className="ow-btn" onClick={copyLink}>
          {copied ? <Check size={13} strokeWidth={2.4} /> : <Copy size={13} strokeWidth={2.1} />}
          {copied ? 'Copied' : 'Copy link'}
        </button>
        {/* F2: sr-only live region announces copy confirmation to AT
            independently of where focus is. */}
        <span className="sr-only" aria-live="polite">
          {copied ? 'Link copied to clipboard' : ''}
        </span>
        {/* F5: aria-label announces new-tab behaviour to AT */}
        <a className="ow-btn" href={`/track/${shipment.token}`} target="_blank" rel="noreferrer"
          aria-label="See what they see (opens in new tab)">
          <ExternalLink size={13} strokeWidth={2.1} /> See what they see
        </a>
      </div>
      {/* F4: ow-lock-tight replaces style={{ marginTop: 9 }} */}
      <p className="ow-lock ow-lock-tight">
        <Lock size={14} strokeWidth={2.1} />
        <span>No login needed to read it. It never shows the rider&rsquo;s identity, phone, internal ids or the failure reason.</span>
      </p>

      {/* One timeline, not two. "Attempts" and "History" described the same
          moment twice — an attempt logged at 4:40 appeared as its own card
          and again as an "Attempt failed" event in the list below. The
          attempt rows carry the reason and note, so they are merged into the
          history at their own timestamps and the duplicate event is dropped. */}
      <div className="ow-sec">History</div>
      <div className="ow-card">
        {merged.length === 0 ? (
          /* F4: ow-tl-empty replaces inline style= on the empty history paragraph */
          <p className="ow-tl-empty">No events recorded yet.</p>
        ) : (
          <ul className="ow-tl">
            {merged.map((row) => (
              <li key={row.key} style={{ ['--c' as string]: row.colour }}>
                <span className="t">{formatTime(row.at)}</span>
                <span className="m" />
                <span><b>{row.title}</b><em>{row.detail}</em></span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {isTerminal(shipment.status) && (
        <p className="ow-foot">
          This shipment is closed. It stays in the register and in your reports.
        </p>
      )}
    </div>
  );
}
