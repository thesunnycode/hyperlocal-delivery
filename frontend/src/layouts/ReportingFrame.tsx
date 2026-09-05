import { Outlet } from 'react-router-dom';

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
 */
export default function ReportingFrame() {
  return (
    <div className="rp rp-embed">
      <Outlet />
    </div>
  );
}
