import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuthStore } from "@/store/auth";
import { Spinner } from "./Spinner";

export function ProtectedRoute() {
  const { isAuthenticated, initializing } = useAuthStore();
  const location = useLocation();

  if (initializing) return <Spinner />;
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  return <Outlet />;
}
