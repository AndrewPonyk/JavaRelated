export function Spinner({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-8 text-gray-500" role="status">
      <span className="h-5 w-5 animate-spin rounded-full border-2 border-gray-300 border-t-brand" />
      <span>{label}</span>
    </div>
  );
}

export function ErrorBanner({
  message,
  onRetry,
}: {
  message: string;
  onRetry?: () => void;
}) {
  return (
    <div className="rounded-md bg-red-50 p-4 text-red-700" role="alert">
      <p>{message}</p>
      {onRetry && (
        <button
          onClick={onRetry}
          className="mt-2 rounded bg-red-600 px-3 py-1 text-sm text-white hover:bg-red-700"
        >
          Retry
        </button>
      )}
    </div>
  );
}
