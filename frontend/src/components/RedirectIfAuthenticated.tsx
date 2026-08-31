import { Navigate } from 'react-router-dom';
import type { ReactElement } from 'react';
import { useAuth } from '../lib/AuthContext.tsx';

/** Send an already-logged-in user straight to their landing page instead of
 * showing them a login form again. Used to wrap /owner/login, /agent/login,
 * and the root path. */
export default function RedirectIfAuthenticated({
  children,
  fallback
}: {
  children?: ReactElement;
  fallback?: ReactElement;
}) {
  const { isAuthenticated, isOwner } = useAuth();

  if (isAuthenticated) {
    return <Navigate to={isOwner ? '/owner/shipments' : '/agent/assignments'} replace />;
  }
  // Every call site passes exactly one of the two, so the null is unreachable
  // today; it exists because the props cannot express "one or the other".
  return children ?? fallback ?? null;
}
