// Typed fetch client for the backend API.
// Types mirror the backend Pydantic schemas (schemas/query.py, schemas/document.py).

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api/v1";

export interface Citation {
  document_id: string;
  chunk_index: number;
  score: number;
}

export interface QueryResponse {
  answer: string;
  citations: Citation[];
  model_id: string;
  latency_ms: number;
}

export interface DocumentOut {
  id: string;
  filename: string;
  doc_type: string;
  status: string;
  chunk_count: number;
  created_at: string;
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      // TODO: attach the user's bearer token from the auth session.
      Authorization: "Bearer dev-token",
      ...init?.headers,
    },
  });
  if (!res.ok) {
    let detail = res.statusText;
    try {
      detail = (await res.json())?.detail ?? detail;
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(detail, res.status);
  }
  return (await res.json()) as T;
}

export function askQuestion(
  question: string,
  opts: { docType?: string; topK?: number } = {},
): Promise<QueryResponse> {
  return request<QueryResponse>("/query", {
    method: "POST",
    body: JSON.stringify({
      question,
      doc_type: opts.docType ?? null,
      top_k: opts.topK ?? null,
    }),
  });
}

export function listDocuments(): Promise<{ items: DocumentOut[]; total: number }> {
  return request("/documents");
}
