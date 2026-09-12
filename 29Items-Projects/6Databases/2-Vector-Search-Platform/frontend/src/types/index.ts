// Shared types mirroring the backend Pydantic schemas (app/schemas/*).

export type Backend = "memory" | "pgvector" | "pinecone" | "weaviate" | "milvus";
export type SearchMode = "vector" | "keyword" | "hybrid";

export const BACKENDS: Backend[] = ["memory", "pgvector", "pinecone", "weaviate", "milvus"];
export const SEARCH_MODES: SearchMode[] = ["hybrid", "vector", "keyword"];

export interface SearchRequest {
  query: string;
  k?: number;
  backend?: Backend;
  mode?: SearchMode;
  filters?: Record<string, unknown>;
}

export interface SearchResult {
  id: string;
  score: number;
  text: string;
  metadata: Record<string, unknown>;
}

export interface SearchResponse {
  query: string;
  backend: string;
  mode: SearchMode;
  count: number;
  results: SearchResult[];
  took_ms: number | null;
}

// ── Documents ──────────────────────────────────────────
export interface DocumentIngestRequest {
  text: string;
  source?: string | null;
  metadata?: Record<string, unknown>;
  backend?: Backend;
}

export interface DocumentIngestResponse {
  document_id: string;
  chunks_indexed: number;
  backend: string;
}

export interface DocumentSummary {
  id: string;
  source: string | null;
  created_at: string | null;
  num_chunks: number;
}

export interface DocumentListResponse {
  total: number;
  limit: number;
  offset: number;
  items: DocumentSummary[];
}

// ── Benchmarks ─────────────────────────────────────────
export interface QueryCase {
  query: string;
  relevant_ids: string[];
}

export interface BenchmarkRequest {
  backends: Backend[];
  cases: QueryCase[];
  k?: number;
  mode?: SearchMode;
}

export interface BenchmarkResult {
  backend: string;
  k: number;
  num_queries: number;
  recall_at_k: number;
  mrr: number;
  latency_p50_ms: number;
  latency_p95_ms: number;
  qps: number;
}

export interface BenchmarkResponse {
  k: number;
  results: BenchmarkResult[];
}

export interface ApiError {
  error: { code: string; message: string; request_id: string | null };
}
