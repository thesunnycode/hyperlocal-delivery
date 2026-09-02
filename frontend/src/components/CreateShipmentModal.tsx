import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { X, AlertTriangle } from 'lucide-react';
import { useModalBehaviour } from '../lib/useModalBehaviour';

/**
 * What the form hands back. `scheduledAt` is optional here even though
 * CreateShipmentBody requires it: the two date/time inputs are optional, and
 * only a complete pair produces a value.
 */
export type NewShipmentInput = {
  customerName: string;
  customerPhone: string;
  address: string;
  scheduledAt?: string;
};

const EMPTY = { customerName: '', customerPhone: '', address: '', date: '', time: '' };

/**
 * Create, as a side sheet rather than a centred modal — Polar 3e4f2765. The
 * list stays on screen behind it, the form can be tall, and the one optional
 * group collapses into an accordion instead of padding the form for everyone.
 *
 * The primary action is a full-width bar pinned to the bottom of the sheet
 * (Mangomint 614b9e62), so it stays reachable however far the form scrolls.
 *
 * Unlike Mangomint, this sheet keeps its scrim: on that screen the right pane
 * is empty space, here it is the record the owner is reading, and an inline
 * panel would evict it.
 *
 * Date and time are one "delivery window" pair, validated together. Filling
 * only one used to drop the schedule silently — the queue then showed no due
 * time and the customer page had no window, with nothing ever saying so.
 */
export default function CreateShipmentModal({
  open,
  onClose,
  onSubmit,
  onGoToAgents,
  busy
}: {
  open: boolean;
  onClose: () => void;
  onSubmit: (data: NewShipmentInput) => Promise<void> | void;
  /** F5: called when the owner clicks "Go to Agents" inside the no-agents
   *  error. Keeps navigation inside the SPA and avoids a hard href. */
  onGoToAgents?: () => void;
  busy?: boolean;
}) {
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState<string | null>(null);
  const [isAgentError, setIsAgentError] = useState(false);
  const [windowError, setWindowError] = useState<string | null>(null);
  const panel = useModalBehaviour(open, onClose);

  // F1: reset the form whenever the modal closes so stale data does not
  // appear when the owner opens it again after cancelling mid-fill.
  useEffect(() => {
    if (!open) {
      setForm(EMPTY);
      setError(null);
      setIsAgentError(false);
      setWindowError(null);
    }
  }, [open]);

  if (!open) return null;

  const halfWindow = (form.date && !form.time) || (!form.date && form.time);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (busy) return;
    setError(null);
    setIsAgentError(false);
    if (halfWindow) {
      setWindowError(form.date
        ? 'Add a time as well, or clear the date — a date on its own is not saved.'
        : 'Add a date as well, or clear the time — a time on its own is not saved.');
      return;
    }
    setWindowError(null);
    const scheduledAt = form.date && form.time ? `${form.date}T${form.time}` : undefined;
    try {
      await onSubmit({
        customerName: form.customerName,
        customerPhone: form.customerPhone,
        address: form.address,
        scheduledAt
      });
      // On success the parent closes us — reset handled by the open effect above.
    } catch (err) {
      const message = err instanceof Error ? err.message : '';
      // F5: detect the no-agents condition via the error CODE (parent already
      // validated body.code === 'NO_AGENTS_AVAILABLE' and carries it on the
      // thrown Error). This used to match the start of the message text
      // instead, which silently broke once already when the Agent-to-Rider
      // rename changed that copy and nobody was running the app at the time
      // to notice — found only by grepping for every 'agent' string the rename
      // touched. The code isn't display copy, so a future rewrite of the
      // message can't break this check the same way.
      const agentErr = (err as { code?: string } | null)?.code === 'NO_AGENTS_AVAILABLE';
      setIsAgentError(agentErr);
      setError(message || 'Could not create the shipment.');
    }
  };

  return (
    <div className="ow-scrim" role="presentation" onClick={onClose}>
      <aside ref={panel} className="ow-sheet" role="dialog" aria-modal="true"
        aria-labelledby="ow-create-title" onClick={(e) => e.stopPropagation()}>
        <div className="ow-sheet-h">
          <b id="ow-create-title">New shipment</b>
          <button type="button" className="ow-x" data-modal-close aria-label="Close" onClick={onClose}>
            <X size={16} strokeWidth={2.2} />
          </button>
        </div>

        <form id="ow-create-form" onSubmit={handleSubmit} className="ow-sheet-b">
          {error && (
            // F2: role="alert" so AT announces the error on render
            // F6: margin:0 now lives in owner.css — no inline style needed
            <p className="ow-inline-err" role="alert">
              {/* F3: aria-hidden — decorative triangle, error text carries the message */}
              <AlertTriangle size={15} strokeWidth={2.1} aria-hidden="true" />
              <span>
                {error}
                {isAgentError && onGoToAgents && (
                  // F5: button-driven navigation, not hard <a href>
                  <> <button type="button" className="ow-err-link" onClick={onGoToAgents}>
                    Go to Riders →
                  </button></>
                )}
                {isAgentError && !onGoToAgents && (
                  // F5: fallback to Link when no callback provided
                  <> <Link to="/owner/agents" className="ow-err-link">Go to Riders →</Link></>
                )}
              </span>
            </p>
          )}

          <div className="ow-f">
            <label htmlFor="c-name">Customer name</label>
            <input id="c-name" value={form.customerName} required placeholder="Priya Menon"
              onChange={(e) => setForm((f) => ({ ...f, customerName: e.target.value }))} />
          </div>
          <div className="ow-f">
            <label htmlFor="c-phone">Phone</label>
            <input id="c-phone" type="tel" value={form.customerPhone} required placeholder="+91 98455 20114"
              onChange={(e) => setForm((f) => ({ ...f, customerPhone: e.target.value }))} />
          </div>
          <div className="ow-f">
            <label htmlFor="c-addr">Delivery address</label>
            <textarea id="c-addr" value={form.address} required placeholder="Flat 402, Brigade Gardenia"
              onChange={(e) => setForm((f) => ({ ...f, address: e.target.value }))} />
            <p className="hint">Written exactly as the rider will read it at the door.</p>
          </div>

          <div className={`ow-f${windowError ? ' bad' : ''}`}>
            {/* F4: remove individual aria-labels that override the visible group
                label. The group label is associated with c-date via for=. A
                sr-only label gives c-time its own programmatic name without
                overriding anything visible. */}
            <label htmlFor="c-date">Delivery window · optional</label>
            <label htmlFor="c-time" className="sr-only">Delivery time</label>
            {/* F7: ow-window-row replaces style={{ display:'flex', gap:10 }} */}
            <div className="ow-window-row">
              {/* F7: ow-window-input replaces style={{ flex:1 }} */}
              <input id="c-date" type="date" className="ow-window-input"
                value={form.date}
                onChange={(e) => { setForm((f) => ({ ...f, date: e.target.value })); setWindowError(null); }} />
              <input id="c-time" type="time" className="ow-window-input"
                value={form.time}
                onChange={(e) => { setForm((f) => ({ ...f, time: e.target.value })); setWindowError(null); }} />
            </div>
            {windowError
              // F2: role="alert"; F3: aria-hidden on icon
              ? <p className="bad-msg" role="alert">
                  <AlertTriangle size={13} strokeWidth={2.2} aria-hidden="true" />
                  <span>{windowError}</span>
                </p>
              : <p className="hint">Both together or neither — a half-filled window is not saved.</p>}
          </div>

          <details className="ow-acc">
            <summary>Assignment</summary>
            <div className="in">
              {/* F7: ow-acc-hint replaces style={{ marginTop:12 }} */}
              <p className="hint ow-acc-hint">
                Auto-assigned to the least-loaded active rider. There is no manual pick at create
                time — you can reassign it the moment it exists.
              </p>
            </div>
          </details>
        </form>

        <div className="ow-sheet-f">
          {/* F7: ow-sheet-submit replaces style={{ flex:1, justifyContent:'center' }} */}
          <button type="submit" form="ow-create-form" className="ow-btn pri ow-sheet-submit"
            disabled={busy}>
            {busy ? 'Creating…' : 'Create shipment'}
          </button>
          <button type="button" className="ow-linky" onClick={onClose}>Cancel</button>
        </div>
      </aside>
    </div>
  );
}
