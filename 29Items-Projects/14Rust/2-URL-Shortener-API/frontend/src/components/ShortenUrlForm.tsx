import { useState } from "react";
import type { FormEvent } from "react";
import { parseShortUrlResponse, validateLongUrl } from "../validation";
import type { ShortUrlResponse } from "../validation";
const REQUEST_TIMEOUT_MS = 10_000;

async function errorMessage(response: globalThis.Response): Promise<string> {
  try {
    const body = (await response.json()) as { message?: unknown };
    if (typeof body.message === "string" && body.message) return body.message;
  } catch {
    // A proxy or edge server may return an empty/non-JSON error response.
  }
  return "Unable to shorten the URL.";
}

export function ShortenUrlForm({ onCopy }: { onCopy: () => void }) {
  const [longUrl, setLongUrl] = useState("");
  const [result, setResult] = useState<ShortUrlResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationError = validateLongUrl(longUrl);
    if (validationError) {
      setError(validationError);
      return;
    }

    setLoading(true);
    setError(null);
    setResult(null);
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
      const response = await fetch("/api/v1/urls", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ long_url: longUrl }),
        signal: controller.signal,
      });
      if (!response.ok) throw new Error(await errorMessage(response));
      setResult(parseShortUrlResponse(await response.json()));
    } catch (cause) {
      setError(
        cause instanceof DOMException && cause.name === "AbortError"
          ? "The request timed out. Please try again."
          : cause instanceof Error
            ? cause.message
            : "Unexpected error.",
      );
    } finally {
      window.clearTimeout(timeout);
      setLoading(false);
    }
  }

  async function copy() {
    if (!result) return;
    try {
      await navigator.clipboard.writeText(result.short_url);
      onCopy();
    } catch {
      setError("Clipboard access was denied. Copy the link manually.");
    }
  }

  return (
    <section className="card" aria-labelledby="form-title">
      <h2 id="form-title">Create a short link</h2>
      <form onSubmit={submit}>
        <label htmlFor="long-url">Destination URL</label>
        <div className="row">
          <input
            id="long-url"
            type="url"
            placeholder="https://example.com/very-long-link"
            value={longUrl}
            onChange={(event) => setLongUrl(event.target.value)}
            maxLength={2048}
            required
            disabled={loading}
          />
          <button type="submit" disabled={loading}>
            {loading ? "Creating..." : "Shorten"}
          </button>
        </div>
      </form>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {result && (
        <div className="result">
          <a href={result.short_url} target="_blank" rel="noreferrer">
            {result.short_url}
          </a>
          <button className="copy" type="button" onClick={copy}>
            Copy
          </button>
        </div>
      )}
    </section>
  );
}
