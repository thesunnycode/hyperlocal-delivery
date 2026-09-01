import type { ReactNode } from 'react';
import RiderIllustration from './RiderIllustration.tsx';

/**
 * The shared frame behind every unauthenticated screen: sign in, register,
 * forgot password. Panel left on desktop, a cropped hero above a lifted white
 * sheet on mobile. Callers supply the form content and, optionally, the
 * panel's pitch copy.
 *
 * The panel carries a line of copy as well as the illustration. It is 45% of a
 * desktop screen and the first thing anyone sees; a wordmark and a drawing
 * were not earning it. The copy is deliberately about the person's job rather
 * than the product's features — whoever is looking at this screen is a shop
 * owner or a rider, not a buyer being sold to.
 *
 * `pitch` defaults to that acquisition-style copy, which is right for
 * register and forgot-password — screens a stranger or a locked-out user
 * lands on. LoginPage overrides it: someone signing back in already knows
 * what the product does, and re-selling it to them daily is furniture, not
 * information (the returning-user login on e.g. Shopify's admin carries no
 * marketing copy at all — see the 2026-09-09 login audit).
 */
export default function AuthShell({
  children,
  pitch
}: {
  children: ReactNode;
  pitch?: { heading: ReactNode; body: ReactNode };
}) {
  const { heading, body } = pitch ?? {
    heading: 'Every delivery, from the shop door to theirs.',
    body: (
      <>
        Assign a rider, watch a shipment move, and send your customer a link that
        just works — no app for them to install, no calls asking where it is.
      </>
    )
  };

  return (
    <div className="auth">
      {/* Skip link — same pattern as OwnerLayout. AuthShell has no sidebar
          so the only thing to skip past is the illustration panel. */}
      <a className="skip-link" href="#auth-form">Skip to form</a>

      <div className="auth-hero" aria-hidden="true">
        <div className="auth-hero-wm">Hyperlocal</div>
        <RiderIllustration crop="tight" />
      </div>

      <div className="auth-split">
        {/* aria-hidden: the panel carries a decorative illustration and a
            tagline. The h2 inside it would appear before the page's h1 in
            the AT tree and break heading order on every auth screen. */}
        <aside className="auth-art" aria-hidden="true">
          <div className="auth-wordmark">Hyperlocal Delivery</div>
          <div className="auth-art-inner">
            <RiderIllustration />
            <div className="auth-pitch">
              <h2>{heading}</h2>
              <p>{body}</p>
            </div>
          </div>
        </aside>

        <div className="auth-form-col">
          <div className="auth-logo" aria-hidden="true">
            <span className="auth-logo-mark">H</span>Hyperlocal
          </div>
          <main className="auth-form-wrap" id="auth-form" tabIndex={-1}>{children}</main>
        </div>
      </div>
    </div>
  );
}
