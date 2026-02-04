import React, { Component, ErrorInfo, ReactNode } from 'react';

interface Props {
  children: ReactNode;
  fallback?: React.ComponentType<{ error: Error; resetError: () => void }>;
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
}

interface State {
  hasError: boolean;
  error?: Error;
  errorInfo?: ErrorInfo;
}

class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): State {
    // Update state so the next render will show the fallback UI
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    // You can log error to an error reporting service here
    console.error('ErrorBoundary caught an error:', error, errorInfo);

    this.setState({
      error,
      errorInfo,
    });

    // Call optional error handler
    if (this.props.onError) {
      this.props.onError(error, errorInfo);
    }

    // Log to external service in production
    if (process.env.NODE_ENV === 'production') {
      // Here you could send the error to your error reporting service
      // Example: Sentry.captureException(error, { extra: errorInfo });
    }
  }

  resetError = () => {
    this.setState({ hasError: false, error: undefined, errorInfo: undefined });
  };

  render() {
    if (this.state.hasError && this.state.error) {
      // Use custom fallback component if provided
      if (this.props.fallback) {
        const FallbackComponent = this.props.fallback;
        return <FallbackComponent error={this.state.error} resetError={this.resetError} />;
      }

      // Default fallback UI
      return (
        <div className="min-h-screen flex items-center justify-center bg-gray-50">
          <div className="max-w-md w-full bg-white shadow-lg rounded-lg p-6">
            <div className="text-center">
              <div className="mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-red-100 mb-4">
                <svg
                  className="h-6 w-6 text-red-600"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.732 15c-.77.833.192 2.5 1.732 2.5z"
                  />
                </svg>
              </div>
              <h1 className="text-2xl font-bold text-gray-900 mb-2">
                Oops! Something went wrong
              </h1>
              <p className="text-gray-600 mb-6">
                We're sorry, but something unexpected happened. Please try refreshing the page.
              </p>
              <div className="space-y-3">
                <button
                  onClick={this.resetError}
                  className="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
                >
                  Try Again
                </button>
                <button
                  onClick={() => window.location.reload()}
                  className="w-full bg-gray-300 hover:bg-gray-400 text-gray-800 font-medium py-2 px-4 rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-gray-500 focus:ring-offset-2"
                >
                  Reload Page
                </button>
              </div>
              {process.env.NODE_ENV === 'development' && this.state.error && (
                <details className="mt-6 text-left">
                  <summary className="cursor-pointer text-sm text-gray-500 hover:text-gray-700 focus:outline-none">
                    Error Details (Development Only)
                  </summary>
                  <div className="mt-2 space-y-2">
                    <div className="p-3 bg-red-50 rounded text-xs">
                      <strong className="text-red-800">Error:</strong>
                      <pre className="mt-1 text-red-700 whitespace-pre-wrap">
                        {this.state.error.message}
                      </pre>
                    </div>
                    {this.state.error.stack && (
                      <div className="p-3 bg-gray-50 rounded text-xs">
                        <strong className="text-gray-800">Stack Trace:</strong>
                        <pre className="mt-1 text-gray-700 whitespace-pre-wrap overflow-auto max-h-40">
                          {this.state.error.stack}
                        </pre>
                      </div>
                    )}
                    {this.state.errorInfo && (
                      <div className="p-3 bg-gray-50 rounded text-xs">
                        <strong className="text-gray-800">Component Stack:</strong>
                        <pre className="mt-1 text-gray-700 whitespace-pre-wrap overflow-auto max-h-40">
                          {this.state.errorInfo.componentStack}
                        </pre>
                      </div>
                    )}
                  </div>
                </details>
              )}
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export { ErrorBoundary };