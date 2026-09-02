import type { ReactNode } from 'react';
import { useModalBehaviour } from '../lib/useModalBehaviour';
import { cx } from '../utils/format';

/**
 * Confirmation gate for hard-to-reverse actions: the agent's terminal
 * outcomes (Mark delivered, Returned), signing out, and the owner's
 * agent-deactivate. Cheap, reversible actions (advancing a step, reassigning,
 * reactivating) stay a single tap.
 *
 * Escape closes it, Tab is trapped inside it, and focus returns to the trigger
 * on close (useModalBehaviour). Cancel comes first in the DOM so it is the
 * first tab stop and a stray Enter cannot commit a terminal action.
 *
 * `tone="danger"` paints the confirm button destructive. Without it this
 * dialog gave "Deactivate Meera" and "Mark delivered" the same button.
 */
export default function ConfirmDialog({
  open,
  title,
  body,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  tone = 'default',
  onConfirm,
  onCancel
}: {
  open: boolean;
  title: ReactNode;
  body: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: 'default' | 'danger';
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const panel = useModalBehaviour(open, onCancel);
  if (!open) return null;
  return (
    <div className="cd" role="presentation" onClick={onCancel}>
      <div
        ref={panel}
        className="cd-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="cd-title"
        aria-describedby="cd-body"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="cd-title" id="cd-title">{title}</h2>
        <p className="cd-body" id="cd-body">{body}</p>
        <div className="cd-acts">
          <button type="button" className="cd-btn" onClick={onCancel}>{cancelLabel}</button>
          <button type="button" className={cx('cd-btn', tone === 'danger' ? 'danger' : 'pri')}
            onClick={onConfirm}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
