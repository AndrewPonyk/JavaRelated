import { useState } from "react";
import { authStore } from "./api/client";
import { DashboardPage } from "./pages/DashboardPage";
import { LoginPage } from "./pages/LoginPage";
import { useChartTheme } from "./theme";

interface AuthState {
  token: string | null;
  roles: string[];
}

export default function App() {
  const theme = useChartTheme();
  const [auth, setAuth] = useState<AuthState>(() => ({
    token: authStore.token,
    roles: authStore.roles,
  }));

  const handleLogin = (token: string, roles: string[]) => {
    authStore.save(token, roles);
    setAuth({ token, roles });
  };

  const handleLogout = () => {
    authStore.clear();
    setAuth({ token: null, roles: [] });
  };

  if (!auth.token) {
    return <LoginPage onLogin={handleLogin} />;
  }

  const canManage = auth.roles.some((role) => role === "operator" || role === "admin");

  return (
    <>
      <header
        style={{
          display: "flex",
          alignItems: "center",
          padding: "14px 24px",
          borderBottom: `1px solid ${theme.gridline}`,
          fontSize: 15,
          fontWeight: 600,
        }}
      >
        Time-Series Analytics
        <button
          onClick={handleLogout}
          style={{
            marginLeft: "auto",
            padding: "4px 12px",
            borderRadius: 6,
            border: `1px solid ${theme.gridline}`,
            background: "transparent",
            color: theme.textSecondary,
            font: "inherit",
            fontSize: 13,
            fontWeight: 400,
            cursor: "pointer",
          }}
        >
          Sign out
        </button>
      </header>
      <DashboardPage canManage={canManage} />
    </>
  );
}
