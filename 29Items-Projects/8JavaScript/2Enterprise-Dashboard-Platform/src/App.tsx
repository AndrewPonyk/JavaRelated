import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { HelmetProvider } from 'react-helmet-async';

// Layout and Authentication
import { Layout } from '@/components/layout/Layout';
import { AuthProvider } from '@/components/auth/AuthProvider';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import { ErrorBoundary } from '@/components/ui/ErrorBoundary';
import { Toaster, ToastProvider } from '@/components/ui/Toaster';
import { CSRFProvider, CSRFGuard } from '@/components/security/CSRFProvider';

// Pages
import { LoginPage } from '@/pages/auth/LoginPage';
import { RegisterPage } from '@/pages/auth/RegisterPage';
import { ForgotPasswordPage } from '@/pages/auth/ForgotPasswordPage';
import { DashboardOverview } from '@/pages/dashboard/Overview';
import { CustomDashboard } from '@/pages/dashboard/CustomDashboard';
import { Reports } from '@/pages/analytics/Reports';
import { Insights } from '@/pages/analytics/Insights';
import { MLAnalytics } from '@/pages/analytics/MLAnalytics';
import { Financial } from '@/pages/analytics/Financial';
import { UserManagement } from '@/pages/admin/UserManagement';
import { SystemSettings } from '@/pages/admin/SystemSettings';
import { Security } from '@/pages/admin/Security';
import { AuditLogs } from '@/pages/admin/AuditLogs';
import { ProfilePage } from '@/pages/profile/ProfilePage';
import { DataSources } from '@/pages/DataSources';
import { MyWidgets } from '@/pages/widgets/MyWidgets';
import { SharedWidgets } from '@/pages/widgets/SharedWidgets';
import { WidgetTemplates } from '@/pages/widgets/WidgetTemplates';
import { Calendar } from '@/pages/Calendar';
import { Notifications } from '@/pages/Notifications';
import { Support } from '@/pages/Support';
import { Settings } from '@/pages/Settings';
import { NotFoundPage } from '@/pages/errors/NotFoundPage';
import { Terms } from '@/pages/legal/Terms';
import { Privacy } from '@/pages/legal/Privacy';

// Utils and Configuration
import { queryClient } from '@/services/cache/queryClient';

// Global Styles
import '@/styles/globals.css';

// Error Fallback Component
const ErrorFallback: React.FC<{ error: Error; resetError: () => void }> = ({ error, resetError }) => (
  <div className="min-h-screen flex items-center justify-center bg-gray-50">
    <div className="max-w-md w-full bg-white shadow-lg rounded-lg p-6">
      <div className="text-center">
        <h1 className="text-2xl font-bold text-red-600 mb-4">
          Something went wrong
        </h1>
        <p className="text-gray-600 mb-6">
          {error.message || 'An unexpected error occurred'}
        </p>
        <div className="space-y-3">
          <button
            onClick={resetError}
            className="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded-md transition-colors"
          >
            Try Again
          </button>
          <button
            onClick={() => window.location.reload()}
            className="w-full bg-gray-300 hover:bg-gray-400 text-gray-800 font-medium py-2 px-4 rounded-md transition-colors"
          >
            Reload Page
          </button>
        </div>
        {process.env.NODE_ENV === 'development' && (
          <details className="mt-6 text-left">
            <summary className="cursor-pointer text-sm text-gray-500 hover:text-gray-700">
              Error Details
            </summary>
            <pre className="mt-2 p-3 bg-gray-100 rounded text-xs overflow-auto">
              {error.stack}
            </pre>
          </details>
        )}
      </div>
    </div>
  </div>
);

// Main App Component
const App: React.FC = () => {
  return (
    <ErrorBoundary fallback={ErrorFallback}>
      <HelmetProvider>
        <QueryClientProvider client={queryClient}>
          <CSRFProvider>
            <CSRFGuard>
              <Router>
                <AuthProvider>
                  <ToastProvider>
                    <div className="App">
                <Routes>
                  {/* Public Auth Routes */}
                  <Route path="/auth/login" element={<LoginPage />} />
                  <Route path="/auth/register" element={<RegisterPage />} />
                  <Route path="/auth/forgot-password" element={<ForgotPasswordPage />} />

                  {/* Public Legal Pages */}
                  <Route path="/legal/terms" element={<Terms />} />
                  <Route path="/legal/privacy" element={<Privacy />} />

                  {/* Protected Routes with Layout */}
                  <Route
                    path="/*"
                    element={
                      <ProtectedRoute>
                        <Layout>
                          <Routes>
                            {/* Dashboard Routes */}
                            <Route path="/" element={<Navigate to="/dashboard" replace />} />
                            <Route path="/dashboard" element={<DashboardOverview />} />
                            <Route path="/dashboard/:id" element={<CustomDashboard />} />

                            {/* Analytics Routes */}
                            <Route path="/analytics/reports" element={<Reports />} />
                            <Route path="/analytics/insights" element={<Insights />} />
                            <Route path="/analytics/ml" element={<MLAnalytics />} />
                            <Route path="/analytics/financial" element={<Financial />} />
                            <Route path="/reports" element={<Reports />} />

                            {/* Data Sources */}
                            <Route path="/data-sources" element={<DataSources />} />

                            {/* Widgets */}
                            <Route path="/widgets" element={<Navigate to="/widgets/my" replace />} />
                            <Route path="/widgets/my" element={<MyWidgets />} />
                            <Route path="/widgets/shared" element={<SharedWidgets />} />
                            <Route path="/widgets/templates" element={<WidgetTemplates />} />

                            {/* Calendar */}
                            <Route path="/calendar" element={<Calendar />} />

                            {/* Admin Routes - Protected by role */}
                            <Route
                              path="/admin/users"
                              element={
                                <ProtectedRoute roles={['SUPER_ADMIN', 'ADMIN']}>
                                  <UserManagement />
                                </ProtectedRoute>
                              }
                            />
                            <Route
                              path="/admin/settings"
                              element={
                                <ProtectedRoute roles={['SUPER_ADMIN', 'ADMIN']}>
                                  <SystemSettings />
                                </ProtectedRoute>
                              }
                            />
                            <Route
                              path="/admin/security"
                              element={
                                <ProtectedRoute roles={['SUPER_ADMIN', 'ADMIN']}>
                                  <Security />
                                </ProtectedRoute>
                              }
                            />
                            <Route
                              path="/admin/audit"
                              element={
                                <ProtectedRoute roles={['SUPER_ADMIN', 'ADMIN']}>
                                  <AuditLogs />
                                </ProtectedRoute>
                              }
                            />

                            {/* User Profile & Settings */}
                            <Route path="/profile" element={<ProfilePage />} />
                            <Route path="/profile/settings" element={<ProfilePage />} />
                            <Route path="/settings" element={<Settings />} />

                            {/* Notifications & Support */}
                            <Route path="/notifications" element={<Notifications />} />
                            <Route path="/support" element={<Support />} />

                            {/* 404 Page */}
                            <Route path="*" element={<NotFoundPage />} />
                          </Routes>
                        </Layout>
                      </ProtectedRoute>
                    }
                  />
                </Routes>

                    {/* Global Components */}
                    <Toaster />
                    </div>
                  </ToastProvider>
                </AuthProvider>
              </Router>
            </CSRFGuard>
          </CSRFProvider>

          {/* Development Tools */}
          {process.env.NODE_ENV === 'development' && (
            <ReactQueryDevtools initialIsOpen={false} />
          )}
        </QueryClientProvider>
      </HelmetProvider>
    </ErrorBoundary>
  );
};

export default App;