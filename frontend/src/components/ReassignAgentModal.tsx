import { useEffect, useState } from 'react';
import { X, Lock } from 'lucide-react';
import { useModalBehaviour } from '../lib/useModalBehaviour';
import type { ShipmentStatus } from '../types/api';

/** What this sheet needs off an agent. AgentSummary from the API satisfies it. */
export type ReassignCandidate = {
  id: number | string;
  name: string;
  openCount: number;
};

/**
 * Choosing who carries a shipment. A side sheet, like create, so the two
 * write paths on this screen behave the same way.
 *
 * The options are a real radio group (shared `name`, group label), so arrow
 * keys move between them and a screen reader announces "3 of 7". Escape
 * closes; Tab stays inside.
 */
export default function ReassignAgentModal({
  open,
  onClose,
  agents,
  currentAgentId,
  shipmentStatus,
  onReassign,
  busy
}: {
  open: boolean;
  onClose: () => void;
  agents: ReassignCandidate[];
  currentAgentId?: number | string | null;
  /** F6: used to show the failed-shipment restore notice only when relevant. */
  shipmentStatus?: ShipmentStatus;
  onReassign: (agentId: number | string) => void;
  busy?: boolean;
}) {
  const [selectedAgentId, setSelectedAgentId] = useState<number | string>('');
  const panel = useModalBehaviour(open, onClose);

  // Reset selection to the current agent each time the sheet opens.
  useEffect(() => {
    if (open) setSelectedAgentId(currentAgentId || '');
    // eslint-disable-next-line react-hooks/exhaustive-deps -- open is the trigger; a currentAgentId change mid-open must not move the radio under the user
  }, [open]);

  if (!open) return null;

  // F2: disable submit when the selection hasn't changed — avoids a no-op
  // round-trip and a misleading "Reassigned to X" toast when nothing moved.
  const unchanged = String(selectedAgentId) === String(currentAgentId ?? '');

  return (
    <div className="ow-scrim" role="presentation" onClick={onClose}>
      <aside ref={panel} className="ow-sheet" role="dialog" aria-modal="true"
        aria-labelledby="ow-reassign-title" onClick={(e) => e.stopPropagation()}>
        <div className="ow-sheet-h">
          <b id="ow-reassign-title">Change rider</b>
          <button type="button" className="ow-x" data-modal-close aria-label="Close" onClick={onClose}>
            <X size={16} strokeWidth={2.2} aria-hidden="true" />
          </button>
        </div>

        <div className="ow-sheet-b">
          {/* F4: ow-radio-list replaces style={{ display:'flex', flexDirection:'column', gap:8 }} */}
          <div role="radiogroup" aria-label="Assign to" className="ow-radio-list">
            <label className={`ow-radio${!selectedAgentId ? ' on' : ''}`}>
              <input type="radio" name="ow-reassign-agent" checked={!selectedAgentId}
                onChange={() => setSelectedAgentId('')} />
              <span className="dot" aria-hidden="true" />
              <span>Auto — least-loaded active rider</span>
            </label>
            {agents.map((a) => (
              <label key={a.id} className={`ow-radio${String(selectedAgentId) === String(a.id) ? ' on' : ''}`}>
                <input type="radio" name="ow-reassign-agent"
                  checked={String(selectedAgentId) === String(a.id)}
                  onChange={() => setSelectedAgentId(a.id)} />
                <span className="dot" aria-hidden="true" />
                {/* F3: aria-hidden dot separator so AT reads "Ravi Kumar, 3 open"
                    rather than "Ravi Kumar middle dot 3 open". */}
                <span>
                  {a.name}
                  <span aria-hidden="true"> · </span>
                  <span className="sr-only">, </span>
                  {a.openCount} open
                </span>
              </label>
            ))}
          </div>

          {/* F1: explain why no named agents appear when the roster is empty */}
          {agents.length === 0 && (
            <p className="ow-lock ow-lock-top">
              <Lock size={14} strokeWidth={2.1} />
              <span>No active riders on the roster. Go to Riders to add or reactivate one.</span>
            </p>
          )}

          {/* F6: show the failed-shipment restore notice only when relevant;
              add the Lock icon to match every other ow-lock instance. */}
          {shipmentStatus === 'failed' && (
            <p className="ow-lock ow-lock-mid">
              <Lock size={14} strokeWidth={2.1} />
              <span>Reassigning also restores this shipment to Assigned — the only status change an owner can make.</span>
            </p>
          )}
        </div>

        <div className="ow-sheet-f">
          {/* F4: ow-sheet-submit replaces style={{ flex:1, justifyContent:'center' }}
              F2: disabled when selection matches current agent */}
          <button type="button" className="ow-btn pri ow-sheet-submit"
            disabled={busy || unchanged} onClick={() => onReassign(selectedAgentId)}>
            {/* F5: "Change rider" aligns with the trigger button label */}
            {busy ? 'Saving…' : 'Change rider'}
          </button>
          <button type="button" className="ow-linky" onClick={onClose}>Cancel</button>
        </div>
      </aside>
    </div>
  );
}
