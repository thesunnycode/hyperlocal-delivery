import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';
import ToastStack from '../components/ToastStack.tsx';

/** Per INTERACTIONS.md: 'accent' for confirmations, 'offline' for the poll-lost note. */
export type ToastTone = 'default' | 'accent' | 'offline';

/**
 * An optional single action carried by a toast.
 *
 * Added by the redesign for one specific job: undo. The owner's queue has a
 * "return this to the queue" action that appears three times on a bad morning,
 * and guarding each one with a confirm dialog taxes the common case (the owner
 * meant it) to protect the rare one (they misclicked). A toast with Undo
 * inverts that — one tap to act, one tap to take it back, and the dialog
 * disappears entirely.
 *
 * Deliberately ONE action, not a list. A toast with two buttons is a dialog
 * that has escaped onto the corner of the screen, and it competes with the
 * page for the same click.
 */
export type ToastAction = {
  label: string;
  /** Runs, then the toast dismisses itself. */
  onAct: () => void;
};

export type Toast = {
  id: number;
  message: string;
  tone: ToastTone;
  action?: ToastAction;
};

export type ToastContextValue = {
  push: (message: string, tone?: ToastTone, action?: ToastAction) => void;
};

const ToastContext = createContext<ToastContextValue | null>(null);

// Transient feedback only — per INTERACTIONS.md a toast never carries the
// only copy of information; it confirms what a banner/lock-note/page state
// already says at rest. tone: 'default' | 'accent' | 'offline'.
/**
 * How long a toast stays up.
 *
 * A confirmation can go quickly — the user already knows what they did. A
 * failure is news, is usually longer, and often arrives while the user is
 * looking somewhere else on the screen; 3.2 seconds was not enough to read
 * "Rider has 4 active shipments; reassign before deactivating" and an error
 * nobody reads is an error that did not happen.
 */
const LIFETIME_MS: Record<ToastTone, number> = {
  default: 3200,
  accent: 9000,
  offline: 9000
};

/**
 * A toast carrying an action has to outlive a plain confirmation: 3.2 seconds
 * is enough to register "done", but not to notice a mistake, move the mouse
 * and click Undo. Seven seconds is the shortest window that reliably survives
 * a glance away from the screen, and it applies regardless of tone — the
 * action is the reason for the delay, not the severity.
 */
const ACTION_LIFETIME_MS = 7000;
const lifetime = (tone: ToastTone, action?: ToastAction) =>
  action ? ACTION_LIFETIME_MS : LIFETIME_MS[tone];

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const timers = useRef<Record<number, ReturnType<typeof setTimeout>>>({});

  const dismiss = useCallback((id: number) => {
    setToasts((t) => t.filter((x) => x.id !== id));
    clearTimeout(timers.current[id]);
    delete timers.current[id];
  }, []);

  const push = useCallback((message: string, tone: ToastTone = 'default', action?: ToastAction) => {
    const id = Date.now() + Math.random();
    setToasts((t) => t.concat([{ id, message, tone, action }]));
    timers.current[id] = setTimeout(() => dismiss(id), lifetime(tone, action));
  }, [dismiss]);

  /* Reading stops the clock. Standard for anything that self-dismisses, and
     the only way to finish a long message that is about to disappear. */
  const hold = useCallback((id: number) => {
    clearTimeout(timers.current[id]);
  }, []);

  const release = useCallback((id: number, tone: ToastTone) => {
    clearTimeout(timers.current[id]);
    const t = toasts.find((x) => x.id === id);
    timers.current[id] = setTimeout(() => dismiss(id), lifetime(tone, t?.action));
  }, [dismiss, toasts]);

  /* Acting dismisses. Leaving the toast up after Undo would invite a second
     click on an action that has already been taken back. */
  const act = useCallback((id: number) => {
    setToasts((t) => {
      t.find((x) => x.id === id)?.action?.onAct();
      return t.filter((x) => x.id !== id);
    });
    clearTimeout(timers.current[id]);
    delete timers.current[id];
  }, []);

  return (
    <ToastContext.Provider value={{ push }}>
      {children}
      <ToastStack toasts={toasts} onDismiss={dismiss} onHold={hold} onRelease={release} onAct={act} />
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue['push'] {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within ToastProvider');
  return ctx.push;
}
