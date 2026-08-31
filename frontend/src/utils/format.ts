export function initials(name = ''): string {
  return name
    .trim()
    .split(/\s+/)
    .map((p) => p[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();
}

/**
 * `hour12: true` is deliberate, not a default.
 *
 * These helpers used to pass only { hour, minute }, which leaves the clock to
 * the viewer's locale — so the same shipment read "4:30 PM" on one phone and
 * "16:30" on the next. Delivery windows are spoken aloud between an agent and
 * a customer, and this product's users are in India, where the 12-hour clock
 * with AM/PM is the spoken convention. Pinning it makes the app say the same
 * thing to everyone.
 *
 * The locale itself stays `undefined` on purpose: day and month order should
 * still follow the viewer. Only the clock is pinned.
 */
const TIME_OPTS: Intl.DateTimeFormatOptions = { hour: 'numeric', minute: '2-digit', hour12: true };
const TIME_OPTS_WITH_DATE: Intl.DateTimeFormatOptions = { day: '2-digit', month: 'short', ...TIME_OPTS };

/**
 * Locales disagree on the meridiem's casing and punctuation — en-US renders
 * "4:30 PM", en-GB "4:30 pm", and some add periods. Since the clock itself is
 * already pinned, the casing is pinned with it so a delivery window looks the
 * same on every device. Locales that do not use a meridiem are untouched.
 */
function upperMeridiem(s: string): string {
  return s.replace(/([ap])\.?\s?m\.?/gi, (_m, p: string) => `${p.toUpperCase()}M`);
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return String(iso);
  return upperMeridiem(d.toLocaleString(undefined, TIME_OPTS_WITH_DATE));
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return String(iso);
  return d.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' });
}

export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return String(iso);
  return upperMeridiem(d.toLocaleTimeString(undefined, TIME_OPTS));
}

// Tracking tokens are full UUIDs — fine as an identifier, too long to sit
// at equal visual weight with a customer or business name. Shorten what's
// shown; the full value stays available via `title` for anyone who needs it.
export function shortToken(token = ''): string {
  return token.length > 10 ? token.slice(0, 8) + '…' : token;
}

export function telHref(phone = ''): string {
  return 'tel:' + phone.replace(/[^+\d]/g, '');
}

/**
 * Directions to a free-text address.
 *
 * Google's universal cross-platform URL rather than a `geo:` URI or an
 * apple/google-specific scheme: it opens the installed Maps app on Android and
 * iOS and falls back to the web everywhere else, which is the whole range of
 * devices a rider might be holding. An empty address yields an empty string so
 * callers can decide not to render a link at all.
 */
export function mapsHref(address = ''): string {
  const trimmed = address.trim();
  if (!trimmed) return '';
  return `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(trimmed)}`;
}

export type ClassName = string | false | null | undefined;

export function cx(...parts: ClassName[]): string {
  return parts.filter(Boolean).join(' ');
}

// A 4xx other than 401/403 is a business-rule rejection the user can act on
// (e.g. NO_AGENTS_AVAILABLE) — it belongs in the form/toast, not the
// full-page fatal-error takeover reserved for true (5xx) failures.
/**
 * True when the failure means "that record is not there" rather than "the
 * server broke". A stale bookmark or a hand-typed id should not be dressed up
 * as a 500 — the caller can recover inline and keep the rest of the screen.
 */
export function isMissingError(err: unknown): boolean {
  return !!err && typeof err === 'object' && 'status' in err
    && (err as { status: unknown }).status === 404;
}

export function isActionableError(err: unknown): boolean {
  if (!err || typeof err !== 'object' || !('status' in err)) return false;
  const status = (err as { status: unknown }).status;
  return (
    typeof status === 'number' &&
    status >= 400 &&
    status < 500 &&
    status !== 401 &&
    status !== 403
  );
}
