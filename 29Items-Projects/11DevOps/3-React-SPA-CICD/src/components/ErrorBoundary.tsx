import { Component, type ErrorInfo, type PropsWithChildren, type ReactNode } from 'react';
import { isRouteErrorResponse, useRouteError } from 'react-router-dom';

import { logger } from '@/lib/logger';

interface ErrorBoundaryProps extends PropsWithChildren {
  /** Custom fallback; defaults to the generic apology + reload. */
  fallback?: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
}

/**
 * Last-line containment (docs/ARCHITECTURE.md §2.6). Route-level failures are caught
 * earlier by RouteErrorFallback; this boundary only sees what escapes the router.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    logger.error('Unhandled render error', {
      message: error.message,
      componentStack: info.componentStack ?? undefined,
    });
  }

  render() {
    if (this.state.hasError) {
      return (
        this.props.fallback ?? (
          <div role="alert" className="error-panel">
            <h1>Something went wrong</h1>
            <p>Sorry — an unexpected error occurred. The team has been notified.</p>
            <button type="button" onClick={() => window.location.reload()}>
              Reload the page
            </button>
          </div>
        )
      );
    }
    return this.props.children;
  }
}

/** errorElement for the router: catches loader/render errors per route without killing the shell. */
export function RouteErrorFallback() {
  const error = useRouteError();

  if (isRouteErrorResponse(error)) {
    logger.warn('Route error response', { status: error.status });
    return (
      <div role="alert" className="error-panel">
        <h1>
          {error.status} — {error.statusText}
        </h1>
        <p>We could not load this page.</p>
      </div>
    );
  }

  logger.error('Route rendering failed', { message: String(error) });
  return (
    <div role="alert" className="error-panel">
      <h1>Something went wrong</h1>
      <p>Sorry — this page failed to load. Please try again.</p>
      <button type="button" onClick={() => window.location.reload()}>
        Reload the page
      </button>
    </div>
  );
}
