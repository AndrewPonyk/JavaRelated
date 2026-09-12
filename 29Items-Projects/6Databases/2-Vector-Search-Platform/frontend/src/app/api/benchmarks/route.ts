// Proxy for running a recall@k benchmark.
import { runBenchmark } from "@/lib/api";
import { parseJson, proxy } from "@/lib/proxy";
import type { BenchmarkRequest } from "@/types";

export async function POST(request: Request) {
  const payload = await parseJson<BenchmarkRequest>(request);
  return proxy(() => runBenchmark(payload));
}
