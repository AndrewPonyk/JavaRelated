"use client";

// Document upload widget. Posts multipart to POST /api/v1/documents and surfaces the
// async ingestion status (the backend returns 202 + a document_id).

import { useState } from "react";

type Status = "idle" | "uploading" | "done" | "error";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api/v1";

export default function DocumentUpload() {
  const [status, setStatus] = useState<Status>("idle");
  const [message, setMessage] = useState<string>("");

  async function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    setStatus("uploading");
    setMessage(`Uploading ${file.name}…`);

    const form = new FormData();
    form.append("file", file);
    form.append("doc_type", "general"); // TODO: surface a doc_type selector (legal | medical)

    try {
      const res = await fetch(`${API_BASE}/documents`, {
        method: "POST",
        headers: { Authorization: "Bearer dev-token" }, // TODO: real auth token
        body: form, // do NOT set Content-Type — the browser sets the multipart boundary
      });
      if (!res.ok) throw new Error((await res.json())?.detail ?? res.statusText);
      const body = await res.json();
      setStatus("done");
      setMessage(`Queued for indexing (id: ${body.document_id}).`);
    } catch (err) {
      setStatus("error");
      setMessage(err instanceof Error ? err.message : "Upload failed.");
    }
  }

  return (
    <div className="upload">
      <label className="upload__label">
        Upload a document
        <input
          type="file"
          accept=".pdf,.docx,.txt"
          onChange={handleChange}
          disabled={status === "uploading"}
        />
      </label>
      {message && (
        <p className={status === "error" ? "upload__error" : "upload__hint"} role="status">
          {message}
        </p>
      )}
    </div>
  );
}
