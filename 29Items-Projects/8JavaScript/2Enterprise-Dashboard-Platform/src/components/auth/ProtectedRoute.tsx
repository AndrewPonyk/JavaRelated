import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthProvider';

interface ProtectedRouteProps {
  children: React.ReactNode;
  roles?: string[];
  requireAuth?: boolean;
  fallback?: React.ReactNode;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  children,
  roles = [],
  requireAuth = true,
  fallback,
}) => {
  const { user, isLoading, isAuthenticated } = useAuth();
  const location = useLocation();

  // Show loading state while checking authentication
  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="flex items-center space-x-2">
          <div className="animate-spin rounded-full h-8 w-8 border-2 border-blue-600 border-t-transparent"></div>
          <span className="text-gray-600 font-medium">Verifying access...</span>
        </div>
      </div>
    );
  }

  // Check authentication requirement
  if (requireAuth && !isAuthenticated) {
    // Redirect to login with return URL
    return (
      <Navigate
        to="/auth/login"
        state={{ from: location }}
        replace
      />
    );
  }

  // Check role requirements
  if (roles.length > 0 && user) {
    const hasRequiredRole = roles.includes(user.role);

    if (!hasRequiredRole) {
      // Show custom fallback or default unauthorized message
      if (fallback) {
        return <>{fallback}</>;
      }

      return <UnauthorizedPage requiredRoles={roles} userRole={user.role} />;
    }
  }

  return <>{children}</>;
};

// Unauthorized Page Component
interface UnauthorizedPageProps {
  requiredRoles: string[];
  userRole: string;
}

const UnauthorizedPage: React.FC<UnauthorizedPageProps> = ({
  requiredRoles,
  userRole,
}) => {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-md w-full bg-white shadow-lg rounded-lg p-8">
        <div className="text-center">
          {/* Lock Icon */}
          <div className="mx-auto flex items-center justify-center h-16 w-16 rounded-full bg-red-100 mb-6">
            <svg
              className="h-8 w-8 text-red-600"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 15v2m-6 0h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
              />
            </svg>
          </div>

          <h1 className="text-2xl font-bold text-gray-900 mb-2">
            Access Denied
          </h1>

          <p className="text-gray-600 mb-6">
            You don't have permission to access this page.
          </p>

          <div className="bg-gray-50 rounded-lg p-4 mb-6">
            <div className="text-sm text-gray-700 space-y-2">
              <div>
                <span className="font-medium">Your role:</span>{' '}
                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800 capitalize">
                  {userRole.toLowerCase().replace('_', ' ')}
                </span>
              </div>
              <div>
                <span className="font-medium">Required roles:</span>
                <div className="mt-1 flex flex-wrap gap-1">
                  {requiredRoles.map(role => (
                    <span
                      key={role}
                      className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800 capitalize"
                    >
                      {role.toLowerCase().replace('_', ' ')}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          </div>

          <div className="space-y-3">
            <button
              onClick={() => window.history.back()}
              className="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
            >
              Go Back
            </button>
            <button
              onClick={() => window.location.href = '/dashboard'}
              className="w-full bg-gray-300 hover:bg-gray-400 text-gray-800 font-medium py-2 px-4 rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-gray-500 focus:ring-offset-2"
            >
              Go to Dashboard
            </button>
          </div>

          <div className="mt-6 text-xs text-gray-500">
            If you believe this is an error, please contact your administrator.
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProtectedRoute;