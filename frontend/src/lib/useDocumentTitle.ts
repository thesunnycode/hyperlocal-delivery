import { useEffect } from 'react';

/**
 * Sets `document.title` for the lifetime of the calling component, restoring
 * the app's own name on unmount.
 *
 * Before this every screen but the customer tracking page (which sets
 * `document.title` by hand) left the tab reading the static "Hyperlocal" from
 * index.html — a browser with the queue, the roster, an agent's shipment and
 * three report tabs all open showed five identical tabs, and history/bookmark
 * entries carried no clue which was which. WCAG 2.4.2 (Page Titled) also
 * expects each view to have one.
 */
export function useDocumentTitle(title: string): void {
  useEffect(() => {
    const previous = document.title;
    document.title = `${title} · Hyperlocal`;
    return () => { document.title = previous; };
  }, [title]);
}
