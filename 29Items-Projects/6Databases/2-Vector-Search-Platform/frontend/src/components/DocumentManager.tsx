"use client";

import { useCallback, useEffect, useState } from "react";

import { BACKENDS } from "@/types";
import type { Backend, DocumentListResponse, DocumentSummary } from "@/types";

export function DocumentManager() {
  const [text, setText] = useState("");
  const [source, setSource] = useState("");
  const [backend, setBackend] = useState<Backend>("memory");

  const [docs, setDocs] = useState<DocumentSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch("/api/documents?limit=100");
      const body = await res.json();
      if (!res.ok) throw new Error(body?.error?.message ?? "Failed to load documents");
      const list = body as DocumentListResponse;
      setDocs(list.items);
      setTotal(list.total);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unexpected error");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onIngest(e: React.FormEvent) {
    e.preventDefault();
    if (text.trim().length < 1) {
      setFormError("Document text is required.");
      return;
    }
    setFormError(null);
    setSubmitting(true);
    try {
      const res = await fetch("/api/documents", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text, source: source || null, backend }),
      });
      const body = await res.json();
      if (!res.ok) throw new Error(body?.error?.message ?? "Ingest failed");
      setText("");
      setSource("");
      await refresh();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Unexpected error");
    } finally {
      setSubmitting(false);
    }
  }

  async function onDelete(id: string) {
    try {
      const res = await fetch(`/api/documents/${encodeURIComponent(id)}`, { method: "DELETE" });
      if (!res.ok && res.status !== 204) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.error?.message ?? "Delete failed");
      }
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unexpected error");
    }
  }

  return (
    <div className="stack">
      <form onSubmit={onIngest} className="stack">
        <textarea
          aria-label="document text"
          placeholder="Paste document text to ingest…"
          value={text}
          onChange={(e) => setText(e.target.value)}
        />
        <div className="form-row">
          <input
            aria-label="source"
            className="grow"
            placeholder="source (optional, e.g. a URL or file id)"
            value={source}
            onChange={(e) => setSource(e.target.value)}
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
          <button type="submit" disabled={submitting}>
            {submitting ? "Ingesting…" : "Ingest"}
          </button>
        </div>
        {formError && <p className="field-error">⚠ {formError}</p>}
      </form>

      <h2 style={{ fontSize: "1.1rem", marginBottom: 0 }}>
        Documents <span className="muted">({total})</span>
      </h2>
      {error && <p className="alert">⚠ {error}</p>}
      {loading ? (
        <p className="muted">Loading…</p>
      ) : docs.length === 0 ? (
        <p className="muted">No documents yet. Ingest one above.</p>
      ) : (
        <ul className="stack" style={{ listStyle: "none", padding: 0, margin: 0 }}>
          {docs.map((d) => (
            <li key={d.id} className="card">
              <div className="result-head">
                <span>{d.id}</span>
                <span>{d.num_chunks} chunks</span>
              </div>
              <div className="form-row" style={{ justifyContent: "space-between", marginTop: 6 }}>
                <span className="muted">{d.source ?? "—"}</span>
                <button className="danger" onClick={() => onDelete(d.id)} type="button">
                  Delete
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
