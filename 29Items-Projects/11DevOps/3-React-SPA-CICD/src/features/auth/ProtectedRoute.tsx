import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { useAuth } from './useAuth';
import { LoadingSpinner } from '@/components/LoadingSpinner';

/**
 * UX-level route guard — real authorization is enforced by the API on every request
 * (docs/ARCHITECTURE.md §2.5). Preserves the deep link so login can return the user.
 */
export function ProtectedRoute() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'unknown') {
    return <LoadingSpinner label="Checking your session…" />;
  }

  if (status === 'anonymous') {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <Outlet />;
}
