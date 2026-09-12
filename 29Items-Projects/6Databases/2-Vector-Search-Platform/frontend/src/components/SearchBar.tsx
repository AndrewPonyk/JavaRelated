"use client";

import { useState } from "react";

import { SearchResults } from "@/components/SearchResults";
import { BACKENDS, SEARCH_MODES } from "@/types";
import type { Backend, SearchMode, SearchResponse } from "@/types";

/**
 * Client component demonstrating the canonical data-fetching pattern:
 * controlled inputs -> POST /api/search (server proxy) -> loading / error / data states.
 */
export function SearchBar() {
  const [query, setQuery] = useState("");
  const [backend, setBackend] = useState<Backend>("memory");
  const [mode, setMode] = useState<SearchMode>("hybrid");

  const [data, setData] = useState<SearchResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!query.trim()) {
      setError("Enter a query first.");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const res = await fetch("/api/search", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ query, k: 10, backend, mode }),
      });
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body?.error?.message ?? "Search failed");
      }
      setData(body as SearchResponse);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unexpected error");
      setData(null);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="stack">
      <form onSubmit={onSubmit} className="form-row">
        <input
          aria-label="query"
          className="grow"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search enterprise documents…"
        />
        <select
          aria-label="backend"
          value={backend}
          onChange={(e) => setBackend(e.target.value as Backend)}
        >
          {BACKENDS.map((b) => (
            <option key={b} value={b}>
              {b}
            </option>
          ))}
        </select>
        <select aria-label="mode" value={mode} onChange={(e) => setMode(e.target.value as SearchMode)}>
          {SEARCH_MODES.map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </select>
        <button type="submit" disabled={loading}>
          {loading ? "Searching…" : "Search"}
        </button>
      </form>

      <SearchResults data={data} loading={loading} error={error} />
    </div>
  );
}
