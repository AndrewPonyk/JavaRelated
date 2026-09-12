"use client";

import { useState } from "react";

import { BenchmarkPanel } from "@/components/BenchmarkPanel";
import { BACKENDS } from "@/types";
import type { Backend, BenchmarkResponse, QueryCase } from "@/types";

// Parse "query => id1, id2" lines into labeled QueryCases.
function parseCases(raw: string): QueryCase[] {
  return raw
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [query = "", ids = ""] = line.split("=>");
      return {
        query: query.trim(),
        relevant_ids: ids
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean),
      };
    })
    .filter((c) => c.query.length > 0);
}

export function BenchmarkRunner() {
  const [selected, setSelected] = useState<Backend[]>(["memory"]);
  const [k, setK] = useState(5);
  const [casesRaw, setCasesRaw] = useState(
    "vector database semantic search => \nhybrid keyword retrieval => ",
  );

  const [data, setData] = useState<BenchmarkResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function toggleBackend(b: Backend) {
    setSelected((prev) => (prev.includes(b) ? prev.filter((x) => x !== b) : [...prev, b]));
  }

  async function onRun(e: React.FormEvent) {
    e.preventDefault();
    const cases = parseCases(casesRaw);
    if (selected.length === 0) {
      setError("Select at least one backend.");
      return;
    }
    if (cases.length === 0) {
      setError("Add at least one query case (format: 'query => id1, id2').");
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const res = await fetch("/api/benchmarks", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ backends: selected, cases, k, mode: "vector" }),
      });
      const body = await res.json();
      if (!res.ok) throw new Error(body?.error?.message ?? "Benchmark failed");
      setData(body as BenchmarkResponse);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unexpected error");
      setData(null);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="stack">
      <form onSubmit={onRun} className="stack">
        <div className="form-row">
          {BACKENDS.map((b) => (
            <label key={b} className="muted" style={{ display: "flex", gap: 4, alignItems: "center" }}>
              <input
                type="checkbox"
                checked={selected.includes(b)}
                onChange={() => toggleBackend(b)}
              />
              {b}
            </label>
          ))}
          <label className="muted" style={{ display: "flex", gap: 6, alignItems: "center" }}>
            k
            <input
              type="number"
              min={1}
              max={100}
              value={k}
              onChange={(e) => setK(Number(e.target.value))}
              style={{ width: 70 }}
            />
          </label>
        </div>
        <textarea
          aria-label="cases"
          value={casesRaw}
          onChange={(e) => setCasesRaw(e.target.value)}
          placeholder="one case per line:  query text => relevantDocId1, relevantDocId2"
        />
        <div className="form-row">
          <button type="submit" disabled={loading}>
            {loading ? "Running…" : "Run benchmark"}
          </button>
          <span className="muted" style={{ fontSize: 13 }}>
            Tip: ingest documents first, then paste their ids as the relevant ids.
          </span>
        </div>
        {error && <p className="field-error">⚠ {error}</p>}
      </form>

      {data && <BenchmarkPanel results={data.results} />}
    </div>
  );
}
