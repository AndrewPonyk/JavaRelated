/**
 * App shell: three views behind a lightweight tab switcher (deliberately no
 * router dependency — the SPA has exactly three screens; introduce one when
 * deep links are needed).
 */

import { useState } from "react";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import { AuthPanel } from "./components/AuthPanel";
import { ComputationsPage } from "./components/ComputationsPage";
import { EquationSolver } from "./components/EquationSolver";
import { PlotViewer } from "./components/PlotViewer";

type View = "solve" | "computations" | "account";

function Shell() {
  const [view, setView] = useState<View>("solve");
  const { user, initializing } = useAuth();

  return (
    <main className="app-shell">
      <header>
        <h1>Scientific Computing Platform</h1>
        <p className="muted">
          Symbolic math, numerical methods, and interactive plots — for the classroom.
        </p>
        <nav className="tabs" aria-label="Main navigation">
          <TabButton current={view} value="solve" onSelect={setView}>
            Solve
          </TabButton>
          <TabButton current={view} value="computations" onSelect={setView}>
            My computations
          </TabButton>
          <TabButton current={view} value="account" onSelect={setView}>
            {user ? user.email : "Sign in"}
          </TabButton>
        </nav>
      </header>

      {view === "solve" && (
        <>
          <EquationSolver />
          <PlotViewer expression="sin(x)/x" xMin={-15} xMax={15} />
        </>
      )}

      {view === "computations" &&
        (initializing ? (
          <p className="muted">Checking session…</p>
        ) : user ? (
          <ComputationsPage />
        ) : (
          <section className="card">
            <p className="muted">Sign in to run and track background computations.</p>
            <AuthPanel />
          </section>
        ))}

      {view === "account" && <AuthPanel />}
    </main>
  );
}

function TabButton({
  current,
  value,
  onSelect,
  children,
}: {
  current: View;
  value: View;
  onSelect: (view: View) => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      className={current === value ? "tab tab-active" : "tab"}
      aria-current={current === value ? "page" : undefined}
      onClick={() => onSelect(value)}
    >
      {children}
    </button>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <Shell />
    </AuthProvider>
  );
}
