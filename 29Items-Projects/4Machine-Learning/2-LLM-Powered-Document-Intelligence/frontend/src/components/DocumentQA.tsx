"use client";

// Reference frontend component (deliverable 4.1).
// Demonstrates: data fetching via the typed API client, and explicit
// loading / error / empty / success states.

import { useState } from "react";
import { askQuestion, ApiError, type QueryResponse } from "@/lib/api";

type Status = "idle" | "loading" | "success" | "error";

export default function DocumentQA() {
  const [question, setQuestion] = useState("");
  const [status, setStatus] = useState<Status>("idle");
  const [result, setResult] = useState<QueryResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!question.trim()) return;

    setStatus("loading");
    setError(null);
    setResult(null);

    try {
      const res = await askQuestion(question.trim());
      setResult(res);
      setStatus("success");
    } catch (err) {
      const message =
        err instanceof ApiError ? err.message : "Something went wrong. Please try again.";
      setError(message);
      setStatus("error");
    }
  }

  return (
    <section className="qa">
      <form onSubmit={handleSubmit} className="qa__form">
        <label htmlFor="question" className="qa__label">
          Ask a question about your documents
        </label>
        <textarea
          id="question"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="e.g. What is the termination clause in the master services agreement?"
          rows={3}
          className="qa__input"
        />
        <button type="submit" disabled={status === "loading"} className="qa__submit">
          {status === "loading" ? "Searching…" : "Ask"}
        </button>
      </form>

      {/* Loading state */}
      {status === "loading" && <p className="qa__hint">Retrieving relevant passages…</p>}

      {/* Error state */}
      {status === "error" && (
        <p role="alert" className="qa__error">
          {error}
        </p>
      )}

      {/* Success state */}
      {status === "success" && result && (
        <article className="qa__answer">
          <p>{result.answer}</p>

          {result.citations.length > 0 ? (
            <details className="qa__citations">
              <summary>{result.citations.length} source(s)</summary>
              <ul>
                {result.citations.map((c, i) => (
                  <li key={`${c.document_id}-${c.chunk_index}-${i}`}>
                    <code>
                      {c.document_id}#{c.chunk_index}
                    </code>{" "}
                    — score {c.score.toFixed(3)}
                  </li>
                ))}
              </ul>
            </details>
          ) : (
            <p className="qa__hint">No sources cited.</p>
          )}

          <footer className="qa__meta">
            {result.model_id} · {result.latency_ms} ms
          </footer>
        </article>
      )}
    </section>
  );
}
