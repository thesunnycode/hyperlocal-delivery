import { NavLink, Outlet } from 'react-router-dom';

/**
 * The reporting section, mounted inside the owner console rather than beside
 * it.
 *
 * This replaces AdminLayout, which was a whole second shell — its own sidebar,
 * its own brand block, its own signed-in footer, its own 401/403 guards — for
 * the same person who was already signed in next door. An owner moving from
 * the queue to a chart changed furniture, and the two shells had drifted:
 * "Sign out" in one user footer against "Owner · account" in the other, two
 * pages called Register, two status-pill styles for the same enum.
 *
 * The guards are gone because they are redundant here: this frame only ever
 * renders inside ProtectedRoute role="OWNER", which already answers both the
 * signed-out and the wrong-role cases for the whole console.
 *
 * `.rp` stays as the scope class so reports.css needs no rewrite — its tokens
 * are value-identical to the owner scope's, so nothing shifts visually. Only
 * the shell rules (.rp-app, .rp-side, .rp-who) go unused.
 *
 * The `.rp-tabs` strip below only renders visually under 1050px (see
 * reports.css) — OwnerLayout's own "Reporting" nav group (Overview / Trend /
 * Rider performance) already covers ≥1050px and this would just duplicate it.
 * Below that width the top nav is replaced by a single bottom "Reports" tab
 * that always opens Overview, and nothing else on the page could reach Trend
 * or Rider performance at all — the report existed, and the console offered
 * no way to open it below 1050px wide.
 */
const TABS = [
  { to: '/owner/reports/overview', label: 'Overview' },
  { to: '/owner/reports/trend', label: 'Trend' },
  { to: '/owner/reports/agents', label: 'Rider performance' }
];

export default function ReportingFrame() {
  return (
    <div className="rp rp-embed">
      <nav className="rp-tabs" aria-label="Reporting">
        {TABS.map((t) => (
          <NavLink key={t.to} to={t.to}
            className={({ isActive }) => (isActive ? 'active' : undefined)}>
            {t.label}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </div>
  );
}
