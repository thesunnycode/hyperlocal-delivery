import { Navigate, useLocation } from 'react-router-dom';
import type { ReactElement } from 'react';
import { useAuth } from '../lib/AuthContext.tsx';
import type { UserRole } from '../types/api';

/** Gate a route by role. `role` — 'OWNER' | 'AGENT'. */
export default function ProtectedRoute({
  role,
  children
}: {
  role?: UserRole;
  children: ReactElement;
}) {
  const { isAuthenticated, role: currentRole } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    // Always go to the single sign-in door. The old split sent agent routes
    // to /agent/login and owner routes to /owner/login, both of which are
    // <Navigate to="/login"> in App.tsx — a guaranteed double redirect on
    // every unauthenticated bounce. One hop is enough.
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  if (role && currentRole !== role) {
    return <Navigate to={currentRole === 'AGENT' ? '/agent/assignments' : '/owner/shipments'} replace />;
  }
  return children;
}
