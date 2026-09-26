import { useLocation } from 'react-router-dom';
import { CheckCircle2, AlertTriangle, WifiOff, X, Undo2, type LucideIcon } from 'lucide-react';
import { cx } from '../utils/format';
import type { Toast, ToastTone } from '../lib/ToastContext.tsx';

const ICON_BY_TONE: Record<ToastTone, LucideIcon> = {
  default: CheckCircle2,
  accent: AlertTriangle,
  offline: WifiOff
};

/**
 * Bottom-pinned toast stack. Mounted once at the app root via ToastContext;
 * pages call useToast() rather than rendering this directly.
 *
 * The stack is a live region so a confirmation or a rejection is announced
 * rather than only drawn: exceptions assert (role="alert"), everything else is
 * polite. Icons are decorative — the message text carries the meaning.
 *
 * A toast may carry ONE action, which in practice is always Undo. It renders
 * as a bordered ghost button before the close control, so the reading order is
 * message → what you can do about it → dismiss.
 */
export default function ToastStack({
  toasts,
  onDismiss,
  onHold,
  onRelease,
  onAct
}: {
  toasts: Toast[];
  onDismiss: (id: number) => void;
  /** Pause the auto-dismiss clock while the toast is being read. */
  onHold?: (id: number) => void;
  onRelease?: (id: number, tone: ToastTone) => void;
  /** Run the toast's action, then dismiss it. */
  onAct?: (id: number) => void;
}) {
  // Rider screens keep a sticky action dock pinned to the bottom
  // (`AgentShipmentDetailPage`'s `.ag-dock`) — the one legal next move,
  // always in reach on a screen used one-handed at a door. A bottom-pinned
  // toast used to land exactly on top of it: the moment a status change
  // revealed "Log failed attempt" and "Return", the confirmation toast
  // covered both, and hovering/tapping there paused its own auto-dismiss
  // timer, freezing it in place. Anchor to the top on every `/agent` route
  // instead, clear of the dock on every screen that has one.
  const { pathname } = useLocation();
  const topAnchored = pathname.startsWith('/agent');

  return (
    /* The container is the live region. Items no longer carry role="alert" as
       well — a polite container plus an assertive child announced the same
       message twice on NVDA and JAWS. The container's politeness is enough:
       these are consequences of something the user just did. */
    <div className={cx('ts', topAnchored && 'ts-top')} aria-live="polite" aria-relevant="additions text">
      {toasts.map((t) => {
        const Icon = ICON_BY_TONE[t.tone] || CheckCircle2;
        return (
          <div
            key={t.id}
            className={cx('ts-item', t.tone === 'accent' && 'accent')}
            onMouseEnter={() => onHold?.(t.id)}
            onMouseLeave={() => onRelease?.(t.id, t.tone)}
            onFocusCapture={() => onHold?.(t.id)}
            onBlurCapture={() => onRelease?.(t.id, t.tone)}
          >
            <Icon size={16} strokeWidth={2.2} aria-hidden="true" />
            <span className="ts-msg">{t.message}</span>
            {t.action && (
              <button type="button" className="ts-act" onClick={() => onAct?.(t.id)}>
                <Undo2 size={13} strokeWidth={2.3} aria-hidden="true" />
                {t.action.label}
              </button>
            )}
            <button type="button" className="ts-close" aria-label="Dismiss notification" onClick={() => onDismiss(t.id)}>
              <X size={14} strokeWidth={2.2} aria-hidden="true" />
            </button>
          </div>
        );
      })}
    </div>
  );
}
