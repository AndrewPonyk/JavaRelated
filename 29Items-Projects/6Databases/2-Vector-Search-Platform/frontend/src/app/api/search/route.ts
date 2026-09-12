// Server-side proxy: browser -> this route -> backend (with the secret API key attached here).
import { search } from "@/lib/api";
import { parseJson, proxy } from "@/lib/proxy";
import type { SearchRequest } from "@/types";

export async function POST(request: Request) {
  const payload = await parseJson<SearchRequest>(request);
  return proxy(() => search(payload));
}
