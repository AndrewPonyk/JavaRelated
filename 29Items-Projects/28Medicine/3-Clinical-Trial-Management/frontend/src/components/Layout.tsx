// App shell: top nav + routed content. Hides write-only nav for read-only roles.

import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "@/auth/AuthContext";

const NAV = [
  { to: "/studies", label: "Studies" },
  { to: "/patients", label: "Subjects" },
  { to: "/enrollments", label: "Enrollment" },
  { to: "/eligibility", label: "Eligibility" },
  { to: "/notifications", label: "Notifications" },
];

export function Layout() {
  const { user, logout } = useAuth();
  return (
    <div className="app">
      <header className="topbar">
        <span className="brand">CTMS</span>
        <nav className="nav">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => (isActive ? "active" : "")}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="user-box">
          {user && (
            <span className="muted">
              {user.username} · {user.role}
            </span>
          )}
          <button onClick={logout}>Sign out</button>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
