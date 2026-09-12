// Route table with auth-gated routes.
import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "@/auth/AuthContext";
import { Layout } from "@/components/Layout";
import { LoginPage } from "@/features/auth/LoginPage";
import { StudiesPage } from "@/features/trials/StudiesPage";
import { PatientsPage } from "@/features/patients/PatientsPage";
import { EnrollmentPage } from "@/features/enrollment/EnrollmentPage";
import { EligibilityPage } from "@/features/eligibility/EligibilityPage";
import { NotificationsPage } from "@/features/notifications/NotificationsPage";
import type { ReactElement } from "react";

function RequireAuth({ children }: { children: ReactElement }) {
  const { user, loading } = useAuth();
  if (loading) return <p className="muted center">Loading…</p>;
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/studies" replace />} />
        <Route path="studies" element={<StudiesPage />} />
        <Route path="patients" element={<PatientsPage />} />
        <Route path="enrollments" element={<EnrollmentPage />} />
        <Route path="eligibility" element={<EligibilityPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
