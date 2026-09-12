// Renders children only once data has loaded; shows loading / error otherwise.
import type { ReactNode } from "react";

interface Props {
  loading: boolean;
  error: string | null;
  children: ReactNode;
}

export function AsyncBlock({ loading, error, children }: Props) {
  if (loading) return <p className="muted">Loading…</p>;
  if (error) return <p className="error">Error: {error}</p>;
  return <>{children}</>;
}
