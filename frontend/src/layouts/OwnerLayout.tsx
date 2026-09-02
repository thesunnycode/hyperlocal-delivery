import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { Package, Users, User, LayoutGrid, BarChart3, TrendingUp, Gauge } from 'lucide-react';
import { useAuth } from '../lib/AuthContext.tsx';
import { useFatalError } from '../lib/FatalErrorContext.tsx';
import { cx } from '../utils/format.ts';
import OwnerFatalScreen from '../components/OwnerFatalScreen.tsx';
import type { LucideIcon } from 'lucide-react';

/** A nav slot. The two flags pick which width shows it. */
type NavItem = {
  to: string;
  label: string;
  icon: LucideIcon;
  desktopOnly?: boolean;
  mobileOnly?: boolean;
};

const NAV: NavItem[] = [
  { to: '/owner/shipments', label: 'Shipments', icon: Package },
  { to: '/owner/register', label: 'Register', icon: LayoutGrid, desktopOnly: true },
  { to: '/owner/agents', label: 'Riders', icon: Users },
  { to: '/owner/account', label: 'Account', icon: User, mobileOnly: true },
  { to: '/owner/reports/overview', label: 'Reports', icon: BarChart3, mobileOnly: true }
];

const INSIGHT: NavItem[] = [
  { to: '/owner/reports/overview', label: 'Overview', icon: BarChart3 },
  { to: '/owner/reports/trend', label: 'Trend', icon: TrendingUp },
  { to: '/owner/reports/agents', label: 'Rider performance', icon: Gauge }
];

/**
 * The operations shell — a slim TOP BAR on desktop, the same bottom tab bar on
 * mobile the previous layout already shipped.
 *
 * ── Why the sidebar is gone ───────────────────────────────────────────────
 * The previous comment here argued for a sidebar on the grounds that "every
 * console in the Refero set uses one" and that a top nav "stops scaling past
 * about four sections". Both claims are true of a product with many modules.
 * This one has six destinations and will not grow: a shop owner running five
 * riders has Shipments, Register, Riders, and three read-only reports.
 *
 * What the sidebar cost was the thing the redesign was commissioned to fix.
 * A 216px rail on the left of every screen meant that Shipments, Register,
 * Riders, Account and all three reports opened with an identical 216px of
 * furniture and a page title — so the console read as one screen with the
 * content swapped, which is exactly the feedback this revamp started from.
 * Removing it lets each page's own shape be the thing you recognise, and the
 * queue in particular gets its 216px back on the axis where it was starved.
 *
 * The reporting shell follows the same bar, so moving between operations and
 * insight still is not a change of furniture.
 *
 * Kept exactly as before: the route set, the ProtectedRoute guard above this
 * component, the mobile tab bar as its own element rather than the nav
 * reflowed, Register staying desktop-only (seven columns cannot be read on a
 * phone; the same shipments are on the Shipments tab), the avatar opening the
 * account page rather than signing out on one click, and the two nav
 * landmarks carrying distinct accessible names.
 */
export default function OwnerLayout() {
  const { user } = useAuth();
  const { error } = useFatalError();
  const navigate = useNavigate();

  if (error) return <OwnerFatalScreen />;

  const label = (user?.businessName ?? user?.name ?? '').trim();
  const parts = label.split(/\s+/).filter(Boolean);
  const first = parts[0] ?? '';
  const last = parts[parts.length - 1] ?? '';
  const mark = !first ? 'HL'
    : parts.length === 1 ? first.slice(0, 2).toUpperCase()
      : `${first.charAt(0)}${last.charAt(0)}`.toUpperCase();

  return (
    <div className="ow">
      <a className="skip-link" href="#ow-main">Skip to content</a>
      <div className="ow-app">

        <header className="ow-top">
          <div className="ow-top-in">
            <div className="ow-brand">
              <span className="mk" aria-hidden="true">{mark}</span>
              <span><b>{user?.businessName || 'Hyperlocal'}</b></span>
            </div>

            <nav className="ow-topnav" aria-label="Operations">
              {NAV.filter((n) => !n.mobileOnly).map((n) => (
                <NavLink key={n.to} to={n.to}
                  className={({ isActive }) => (isActive ? 'active' : undefined)}>
                  <n.icon size={15} strokeWidth={2.1} aria-hidden="true" />
                  <span>{n.label}</span>
                </NavLink>
              ))}
            </nav>

            <span className="ow-top-sep" aria-hidden="true" />

            <nav className="ow-topnav" aria-label="Reporting">
              {INSIGHT.map((n) => (
                <NavLink key={n.to} to={n.to}
                  className={({ isActive }) => (isActive ? 'active' : undefined)}>
                  <n.icon size={15} strokeWidth={2.1} aria-hidden="true" />
                  <span>{n.label}</span>
                </NavLink>
              ))}
            </nav>

            <button type="button" className="ow-who"
              onClick={() => navigate('/owner/account')}
              aria-label={`Signed in as ${user?.name || 'owner'}. Open my account`}>
              <span className="av" aria-hidden="true">
                {(user?.name ?? 'O').charAt(0).toUpperCase()}
              </span>
              <span><b>{user?.name || 'Owner'}</b><span>Owner</span></span>
            </button>
          </div>
        </header>

        <main className="ow-main" id="ow-main" tabIndex={-1}>
          <Outlet />
        </main>
      </div>

      {/* `desktopOnly` items (Register) render here too, not just in the top
          nav: the tab bar is now the ONLY nav visible from 760px up to the
          top nav's own 1050px breakpoint (see scopes.css), and Register's
          page renders its real desktop table down to 759px — a narrower
          cutoff than the tab bar's. Filtering them out here left Register
          unreachable by any navigation control across that whole band. CSS
          hides `.ow-tab-desktop-only` specifically below 759px, matching
          the page's own "desktop only" notice instead of the tab bar's. */}
      <nav className="ow-tabs" aria-label="Tab bar">
        {NAV.map((n) => (
          <NavLink key={n.to} to={n.to}
            className={({ isActive }) => cx(n.desktopOnly && 'ow-tab-desktop-only', isActive && 'active')}>
            <n.icon size={19} strokeWidth={2} aria-hidden="true" />
            <span>{n.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
