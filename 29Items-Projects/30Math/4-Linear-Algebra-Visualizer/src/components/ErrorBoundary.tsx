import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Last line of defense (ARCHITECTURE.md §2.6): a render crash becomes a
 * recoverable panel instead of a white page.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Structured console report; an error-telemetry sink (e.g. Sentry) plugs
    // in here once the project has an account for it (PROJECT-PLAN roadmap).
    console.error('Unhandled render error', error, info.componentStack);
  }

  render(): ReactNode {
    if (this.state.error) {
      return (
        this.props.fallback ?? (
          <div className="error-fallback" role="alert">
            <h2>Something went wrong.</h2>
            <p>The application hit an unexpected error. Your progress is safe.</p>
            <button type="button" onClick={() => this.setState({ error: null })}>
              Try again
            </button>
          </div>
        )
      );
    }
    return this.props.children;
  }
}
