import { useState, type ReactNode } from 'react';
import { Copy, Check, LifeBuoy, type LucideIcon } from 'lucide-react';
import { cx } from '../utils/format';

/** Up to two of these. The first is the filled button; the second a text link. */
export type EdgeAction = {
  label: string;
  onClick?: () => void;
  icon?: LucideIcon;
};

/**
 * Where the support contact comes from.
 *
 * Read from the build environment rather than hardcoded, and with NO fallback
 * on purpose. An error screen that prints an invented address is worse than
 * one that prints none — the user writes to nobody and concludes the product
 * is broken twice. If neither var is set at build time the block does not
 * render and the screen is exactly as it was.
 *
 *   VITE_SUPPORT_EMAIL=support@example.in
 *   VITE_SUPPORT_PHONE=+91 80 0000 0000
 *   VITE_SUPPORT_HOURS=9am–9pm        (optional)
 */
const SUPPORT_EMAIL = import.meta.env.VITE_SUPPORT_EMAIL as string | undefined;
const SUPPORT_PHONE = import.meta.env.VITE_SUPPORT_PHONE as string | undefined;
const SUPPORT_HOURS = import.meta.env.VITE_SUPPORT_HOURS as string | undefined;
const HAS_SUPPORT = !!(SUPPORT_EMAIL || SUPPORT_PHONE);

/**
 * The one shell behind every 404 / 403 / 500 / expired-session screen.
 *
 * Refero: Acctual 14c8e51e sets the shape — a large error CODE as the visual,
 * a short headline, one line of plain explanation, then two actions at
 * DIFFERENT weights: a filled button and a text link. This card used to render
 * the second action as another boxed button, which made two recovery paths
 * look equally likely. Appwrite bfed2017, Zendesk c809a4a1 and Xbox e72d20c9
 * use the same information order.
 *
 * `code` is the borrow that mattered: a user can quote "404" to whoever they
 * ask for help, but not a compass glyph. States with no meaningful code — an
 * expired session, an empty roster — pass an `icon` instead and keep the tile.
 *
 * `reference` prints a support-searchable id for 500s, which is what Lemon
 * Squeezy 4887bb77 and Descript e0f5a98e both do.
 *
 * `variant`: 'screen' owns the viewport; 'inline' sits inside a shell that is
 * still valid — a bad reporting route keeps the console sidebar, because that
 * visitor is a legitimate owner on a stale link.
 *
 * `support` prints a way to reach a human under the actions. The audit finding
 * was that this card told an owner to quote a reference code without saying
 * quote it to WHOM — a dead end dressed as a next step. It is opt-in per
 * screen, because a 404 on a mistyped URL does not warrant a support call and
 * a 500 does.
 */
export default function EdgeStateCard({
  icon: Icon,
  code,
  tone = 'surface',
  variant = 'inline',
  title,
  body,
  actions = [],
  foot,
  reference,
  support = false,
  brand
}: {
  icon?: LucideIcon;
  code?: string;
  tone?: 'surface' | 'accent';
  variant?: 'screen' | 'inline';
  title: ReactNode;
  body: ReactNode;
  actions?: EdgeAction[];
  foot?: ReactNode;
  reference?: string | null;
  /** Show the support contact block, if this build has one configured. */
  support?: boolean;
  /** Shown in the header bar of a 'screen' variant, with the reference. */
  brand?: string;
}) {
  const [copied, setCopied] = useState(false);
  const copyRef = () => {
    // Only reachable from the button below, which renders only when
    // `reference` is set — the fallback is for the compiler, not for a path.
    navigator.clipboard?.writeText(reference ?? '');
    setCopied(true);
    setTimeout(() => setCopied(false), 1600);
  };

  const [primary, secondary] = actions;

  return (
    <div className={cx('es', variant)}>
      {variant === 'screen' && (
        <header className="es-top">
          {/* "H", matching the auth shell's mark. This said "HL", which made
              a third glyph for the same product — H at the door, HL on the
              error screens, business initials inside. */}
          <span className="es-brand">
            <span className="mk" aria-hidden="true">H</span>
            <b>{brand || 'Hyperlocal'}</b>
          </span>
          {reference && <span className="ref">{reference}</span>}
        </header>
      )}

      <div className="es-main">
        <div className={cx('es-card', tone === 'accent' && 'accent')}>
          {code
            ? <p className="es-code">{code}</p>
            : Icon ? <div className="es-tile"><Icon size={26} strokeWidth={1.9} /></div> : null}

          <h1 className="es-title">{title}</h1>
          <p className="es-body">{body}</p>

          {reference && (
            <div className="es-ref">
              <span>Reference</span>
              <code>{reference}</code>
              <button type="button" onClick={copyRef} className="es-link" style={{ marginLeft: 'auto' }}>
                {copied ? <Check size={13} strokeWidth={2.6} /> : <Copy size={13} strokeWidth={2.2} />}
                {copied ? 'Copied' : 'Copy'}
              </button>
            </div>
          )}

          {(primary || secondary) && (
            <div className="es-acts">
              {primary && (
                <button type="button" className="es-btn" onClick={primary.onClick}>
                  {primary.icon ? <primary.icon size={15} strokeWidth={2} /> : null}
                  {primary.label}
                </button>
              )}
              {secondary && (
                <button type="button" className="es-link" onClick={secondary.onClick}>
                  {secondary.icon ? <secondary.icon size={14} strokeWidth={2} /> : null}
                  {secondary.label}
                </button>
              )}
            </div>
          )}

          {foot ? <p className="es-foot">{foot}</p> : null}

          {support && HAS_SUPPORT && (
            <div className="es-support">
              <LifeBuoy size={15} strokeWidth={2.1} aria-hidden="true" />
              <span>
                Still stuck?{' '}
                {SUPPORT_EMAIL && <a href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</a>}
                {SUPPORT_EMAIL && SUPPORT_PHONE && ' · '}
                {SUPPORT_PHONE && (
                  <a className="es-support-tel" href={`tel:${SUPPORT_PHONE.replace(/[^\d+]/g, '')}`}>
                    {SUPPORT_PHONE}
                  </a>
                )}
                {SUPPORT_HOURS && <span className="es-support-hrs">, {SUPPORT_HOURS}</span>}
              </span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
