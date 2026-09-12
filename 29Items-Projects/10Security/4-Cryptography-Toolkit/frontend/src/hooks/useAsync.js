import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Standard {status, data, error} wrapper around an async call.
 * `run(fn)` executes immediately; `deps` re-runs an auto fn on change.
 * Cancels stale results after unmount.
 */
export function useAsync(autoFn, deps = []) {
  const [status, setStatus] = useState(autoFn ? "loading" : "idle");
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  const execute = useCallback(async (fn) => {
    setStatus("loading");
    setError(null);
    try {
      const result = await fn();
      if (alive.current) {
        setData(result);
        setStatus("ok");
      }
      return result;
    } catch (err) {
      if (alive.current) {
        setError(err?.message ?? String(err));
        setStatus("error");
      }
      return null;
    }
  }, []);

  useEffect(() => {
    if (autoFn) execute(autoFn);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  const reset = useCallback(() => {
    setStatus("idle");
    setData(null);
    setError(null);
  }, []);

  return { status, data, error, run: execute, reset, setStatus, setError };
}
