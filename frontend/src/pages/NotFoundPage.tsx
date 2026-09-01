import { useNavigate } from 'react-router-dom';
import EdgeStateCard from '../components/EdgeStateCard.tsx';
import { useAuth } from '../lib/AuthContext.tsx';

/**
 * The catch-all 404 — a mistyped or stale URL anywhere in the app.
 *
 * Where "home" goes depends on who is asking, which is why this reads the
 * auth state: an agent sent to the owner console would meet a 403 straight
 * after the 404, and a signed-out visitor has no console at all.
 *
 * There is deliberately no second action for a signed-out visitor. The only
 * thing they can reach without an account is a tracking link, and we cannot
 * build one for them — so that is a sentence, not a button that goes nowhere.
 *
 * Support contact only for signed-in users. A stranger who mistyped a URL does
 * not need a support line, and offering one invites a call about a typo; a
 * signed-in owner who reached a dead end followed a link inside the product,
 * which is a bug worth hearing about.
 */
export default function NotFoundPage() {
  const navigate = useNavigate();
  const { isAuthenticated, isOwner } = useAuth();
  const home = isAuthenticated ? (isOwner ? '/owner/shipments' : '/agent/assignments') : '/login';
  const homeLabel = isAuthenticated
    ? (isOwner ? 'Back to shipments' : 'Back to my assignments')
    : 'Go to sign in';

  return (
    <EdgeStateCard
      variant="screen"
      code="404"
      title="Page not found"
      body={<>That address doesn&rsquo;t match anything in Hyperlocal. It may be a typo, or a link from a version of the app that has moved on.</>}
      actions={[{ label: homeLabel, onClick: () => navigate(home) }]}
      support={isAuthenticated}
      foot={isAuthenticated
        ? undefined
        : 'Tracking a delivery needs no account — open the link that was sent to you, and it will work as it is.'}
    />
  );
}
