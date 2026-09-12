// Renders consistent loading / error / empty states around async data so feature
// components never render against undefined (TECH-NOTES §3.6).

import type { ReactNode } from "react";

interface Props {
  isLoading: boolean;
  isError: boolean;
  error?: unknown;
  isEmpty?: boolean;
  emptyMessage?: string;
  onRetry?: () => void;
  children: ReactNode;
}

export function AsyncBoundary({
  isLoading,
  isError,
  error,
  isEmpty,
  emptyMessage = "Nothing to show yet.",
  onRetry,
  children,
}: Props) {
  if (isLoading) return <p role="status" className="muted">Loading…</p>;
  if (isError) {
    const message = error instanceof Error ? error.message : "Something went wrong.";
    return (
      <div role="alert" className="error-box">
        <p>{message}</p>
        {onRetry && <button onClick={onRetry}>Retry</button>}
      </div>
    );
  }
  if (isEmpty) return <p className="muted">{emptyMessage}</p>;
  return <>{children}</>;
}
