export function reportClientError(scope, error) {
  window.dispatchEvent(
    new CustomEvent('client:error', {
      detail: {
        scope,
        message: error instanceof Error ? error.message : String(error),
        timestamp: new Date().toISOString()
      }
    })
  );
}
