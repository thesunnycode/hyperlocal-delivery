import { useEffect, useRef } from 'react';

/**
 * Keyboard behaviour every overlay in the app is expected to have and none of
 * them had: Escape closes, Tab is trapped inside the panel, the first control
 * takes focus on open, and focus returns to whatever opened it on close.
 *
 * Presentation-only — it changes no action, no request and no state machine.
 *
 * `onClose` is held in a ref so a caller passing an inline arrow does not
 * re-run the effect on every keystroke (which would steal focus back to the
 * first field mid-typing). The effect depends on `open` alone.
 *
 * Initial focus skips any element marked `data-modal-close` (the sheet's own
 * X button, which every caller puts before the body in markup so the title
 * bar reads first). Every side sheet in the app is a data-entry form, and
 * without this a keyboard or screen-reader user's first landing was "leave,"
 * one Tab press away from the field they actually opened the sheet for. The
 * close button stays in the Tab trap — Shift+Tab from the first real field
 * still reaches it — it just never receives focus automatically.
 *
 * Among the remaining candidates, a checked radio wins over plain DOM order.
 * ReassignAgentModal's radio group renders the current rider pre-checked;
 * without this, initial focus landed on the first radio in the list ("Auto")
 * while the actually-checked one sat elsewhere, unfocused — the opposite of
 * how a native radio group hands off focus (checked member first, or the
 * first member if none is checked).
 *
 * A sheet whose content loads asynchronously (OwnerAgentsPage's read-only
 * detail sheet, fetching the rider by id) has nothing but the close button
 * to focus on the frame it opens — landing there is correct in that instant,
 * not a bug. What would be a bug is staying there once real content arrives.
 * A MutationObserver on the panel re-picks initial focus when the panel's
 * content changes, but only while focus is still sitting on the close
 * button — the moment a user tabs, clicks, or types anywhere themselves,
 * later content changes (a save completing, a list re-rendering) leave
 * their focus alone.
 *
 * Usage:
 *   const panel = useModalBehaviour(open, onClose);
 *   ...
 *   <div ref={panel} role="dialog" aria-modal="true" aria-labelledby="...">
 *     ...
 *     <button data-modal-close onClick={onClose}>...</button>
 */
export function useModalBehaviour<T extends HTMLElement = HTMLDivElement>(
  open: boolean,
  onClose: () => void
) {
  const panelRef = useRef<T | null>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;

  useEffect(() => {
    if (!open) return;
    const root = panelRef.current;
    const previouslyFocused = document.activeElement;

    const FOCUSABLE = [
      'a[href]',
      'button:not([disabled])',
      'input:not([disabled]):not([type="hidden"])',
      'select:not([disabled])',
      'textarea:not([disabled])',
      '[tabindex]:not([tabindex="-1"])'
    ].join(',');

    const focusable = (): HTMLElement[] =>
      Array.from(root?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [])
        .filter((el) => el.offsetWidth > 0 || el.offsetHeight > 0);

    const pickInitial = (): HTMLElement | undefined => {
      const candidates = focusable().filter((el) => !el.hasAttribute('data-modal-close'));
      const checkedRadio = candidates.find(
        (el): el is HTMLInputElement => el instanceof HTMLInputElement && el.type === 'radio' && el.checked
      );
      return checkedRadio ?? candidates[0] ?? focusable()[0];
    };

    // Deferred a frame: a caller's own mount effect (e.g. ReassignAgentModal
    // resetting its selected radio to the shipment's current rider) runs
    // AFTER this one, since hooks fire their effects in call order and this
    // hook is always invoked first. Picking the checked radio synchronously
    // here would see last render's checked state, not the one the caller is
    // about to set — focusing the wrong radio the instant the sheet opens.
    // A rAF runs after that cascading re-render has committed.
    const raf = requestAnimationFrame(() => {
      pickInitial()?.focus();
    });

    // Content that hasn't arrived yet (an async detail fetch) means the
    // close button was the only real candidate at the moment above ran.
    // Re-pick once real content lands — but only while the user hasn't
    // already moved focus themselves.
    const observer = new MutationObserver(() => {
      const active = document.activeElement;
      if (!(active instanceof HTMLElement) || !active.hasAttribute('data-modal-close')) return;
      const next = pickInitial();
      if (next && next !== active) next.focus();
    });
    if (root) observer.observe(root, { childList: true, subtree: true });

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        closeRef.current();
        return;
      }
      if (e.key !== 'Tab') return;
      const list = focusable();
      const first = list[0];
      const last = list[list.length - 1];
      if (!first || !last) return;
      const active = document.activeElement;
      const inside = !!root && !!active && root.contains(active);
      if (e.shiftKey && (active === first || !inside)) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && active === last) {
        e.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', onKeyDown, true);
    return () => {
      cancelAnimationFrame(raf);
      observer.disconnect();
      document.removeEventListener('keydown', onKeyDown, true);
      if (previouslyFocused instanceof HTMLElement) previouslyFocused.focus();
    };
  }, [open]);

  return panelRef;
}
