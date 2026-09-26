import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { X, AlertTriangle } from 'lucide-react';
import { useModalBehaviour } from '../lib/useModalBehaviour';
import { localDateTimeToUtcNaive } from '../utils/format';
import { fieldErrorFrom, type FieldError } from '../utils/apiErrors';

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
  // Per-field errors: from a client-side required check on submit, or from
  // the server naming a field (bad phone format, an over-long address).
  // Rendered with the same `.ow-f.bad` / `.bad-msg` treatment the window
  // pair already uses, and routed through `friendlyValidationMessage` so a
  // Bean Validation regex never reaches the screen (it used to: typing the
  // *exact* phone placeholder shown on this form failed its own pattern and
  // showed `must match "[+\d\s\-]{7,20}"` verbatim).
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const panel = useModalBehaviour(open, onClose);

  // F1: reset the form whenever the modal closes so stale data does not
  // appear when the owner opens it again after cancelling mid-fill.
  useEffect(() => {
    if (!open) {
      setForm(EMPTY);
      setError(null);
      setIsAgentError(false);
      setWindowError(null);
      setFieldErrors({});
    }
  }, [open]);

  /** "Today 6–8 PM", "Tomorrow morning"… quick delivery-window picks. Each
   *  sets a single point in time (the schema has no range, just one
   *  `scheduledAt`) but saves the owner two native pickers for the common
   *  case. Computed from the *local* clock — see localDateTimeToUtcNaive for
   *  why that matters once this leaves the form. */
  const quickPicks = useMemo(() => {
    const now = new Date();
    const at = (daysAhead: number, hour: number) => {
      const d = new Date(now);
      d.setDate(d.getDate() + daysAhead);
      d.setHours(hour, 0, 0, 0);
      const z = (n: number) => String(n).padStart(2, '0');
      return { date: `${d.getFullYear()}-${z(d.getMonth() + 1)}-${z(d.getDate())}`, time: `${z(hour)}:00` };
    };
    const picks = [
      { label: 'This evening', ...at(0, 18) },
      { label: 'Tomorrow morning', ...at(1, 10) },
      { label: 'Tomorrow afternoon', ...at(1, 15) }
    ];
    // Past a picker whose hour has already gone by today reads as a promise
    // to arrive in the past — drop it rather than let the owner tap it.
    return picks.filter((p) => new Date(`${p.date}T${p.time}:00`) > now);
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps -- recomputed each time the sheet opens, not every render

  if (!open) return null;

  const halfWindow = (form.date && !form.time) || (!form.date && form.time);

  const setField = (key: keyof typeof EMPTY) => (value: string) => {
    setForm((f) => ({ ...f, [key]: value }));
    setFieldErrors((fe) => {
      if (!(key in fe)) return fe;
      const next = { ...fe };
      delete next[key];
      return next;
    });
  };

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (busy) return;
    setError(null);
    setIsAgentError(false);

    // Client-side required check, replacing the browser's own native-bubble
    // validation (`noValidate` below) — those bubbles are a third, visually
    // inconsistent error style next to the auth screens' inline banners and
    // this form's own window-pair error.
    const required: Record<string, string> = {};
    if (!form.customerName.trim()) required.customerName = 'Enter the customer’s name.';
    if (!form.customerPhone.trim()) required.customerPhone = 'Enter a phone number.';
    if (!form.address.trim()) required.address = 'Enter the delivery address.';
    if (Object.keys(required).length > 0) {
      setFieldErrors(required);
      return;
    }
    setFieldErrors({});

    if (halfWindow) {
      setWindowError(form.date
        ? 'Add a time as well, or clear the date — a date on its own is not saved.'
        : 'Add a date as well, or clear the time — a time on its own is not saved.');
      return;
    }
    setWindowError(null);
    const scheduledAt = form.date && form.time ? localDateTimeToUtcNaive(form.date, form.time) : undefined;
    try {
      await onSubmit({
        customerName: form.customerName,
        customerPhone: form.customerPhone,
        address: form.address,
        scheduledAt
      });
      // On success the parent closes us — reset handled by the open effect above.
    } catch (err) {
      const fe: FieldError | null = fieldErrorFrom(err);
      if (fe?.field === 'customerPhone' || fe?.field === 'phone') {
        setFieldErrors({ customerPhone: fe.message });
        return;
      }
      if (fe?.field === 'address' || fe?.field === 'deliveryAddress') {
        setFieldErrors({ address: fe.message });
        return;
      }
      if (fe?.field === 'customerName') {
        setFieldErrors({ customerName: fe.message });
        return;
      }
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
      setError((fe ? fe.message : message) || 'Could not create the shipment.');
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

        <form id="ow-create-form" onSubmit={handleSubmit} className="ow-sheet-b" noValidate>
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

          <div className={`ow-f${fieldErrors.customerName ? ' bad' : ''}`}>
            <label htmlFor="c-name">Customer name</label>
            <input id="c-name" value={form.customerName} required placeholder="Priya Menon"
              aria-invalid={fieldErrors.customerName ? 'true' : undefined}
              onChange={(e) => setField('customerName')(e.target.value)} />
            {fieldErrors.customerName && (
              <p className="bad-msg" role="alert">
                <AlertTriangle size={13} strokeWidth={2.2} aria-hidden="true" />
                <span>{fieldErrors.customerName}</span>
              </p>
            )}
          </div>
          <div className={`ow-f${fieldErrors.customerPhone ? ' bad' : ''}`}>
            <label htmlFor="c-phone">Phone</label>
            <input id="c-phone" type="tel" value={form.customerPhone} required placeholder="+91 98455 20114"
              aria-invalid={fieldErrors.customerPhone ? 'true' : undefined}
              onChange={(e) => setField('customerPhone')(e.target.value)} />
            {fieldErrors.customerPhone && (
              <p className="bad-msg" role="alert">
                <AlertTriangle size={13} strokeWidth={2.2} aria-hidden="true" />
                <span>{fieldErrors.customerPhone}</span>
              </p>
            )}
          </div>
          <div className={`ow-f${fieldErrors.address ? ' bad' : ''}`}>
            <label htmlFor="c-addr">Delivery address</label>
            <textarea id="c-addr" value={form.address} required placeholder="Flat 402, Brigade Gardenia"
              aria-invalid={fieldErrors.address ? 'true' : undefined}
              onChange={(e) => setField('address')(e.target.value)} />
            {fieldErrors.address
              ? <p className="bad-msg" role="alert">
                  <AlertTriangle size={13} strokeWidth={2.2} aria-hidden="true" />
                  <span>{fieldErrors.address}</span>
                </p>
              : <p className="hint">Written exactly as the rider will read it at the door.</p>}
          </div>

          <div className={`ow-f${windowError ? ' bad' : ''}`}>
            {/* F4: remove individual aria-labels that override the visible group
                label. The group label is associated with c-date via for=. A
                sr-only label gives c-time its own programmatic name without
                overriding anything visible. */}
            <label htmlFor="c-date">Delivery window · optional</label>
            <label htmlFor="c-time" className="sr-only">Delivery time</label>

            {quickPicks.length > 0 && (
              <div className="ow-quickpicks" role="group" aria-label="Quick delivery window picks">
                {quickPicks.map((p) => (
                  <button key={p.label} type="button" className="ow-chip-sm"
                    aria-pressed={form.date === p.date && form.time === p.time}
                    onClick={() => { setForm((f) => ({ ...f, date: p.date, time: p.time })); setWindowError(null); }}>
                    {p.label}
                  </button>
                ))}
              </div>
            )}

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
