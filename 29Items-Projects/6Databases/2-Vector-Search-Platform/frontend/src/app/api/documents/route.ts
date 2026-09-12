// Proxy for document list (GET) and ingest (POST).
import { ingestDocument, listDocuments } from "@/lib/api";
import { parseJson, proxy } from "@/lib/proxy";
import type { DocumentIngestRequest } from "@/types";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const limit = Number(searchParams.get("limit") ?? 50);
  const offset = Number(searchParams.get("offset") ?? 0);
  return proxy(() => listDocuments(limit, offset));
}

export async function POST(request: Request) {
  const payload = await parseJson<DocumentIngestRequest>(request);
  return proxy(() => ingestDocument(payload));
}
