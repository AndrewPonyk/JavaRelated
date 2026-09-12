import { BenchmarkRunner } from "@/components/BenchmarkRunner";

export default function BenchmarkPage() {
  return (
    <section className="stack">
      <h1>Benchmark</h1>
      <p className="muted">
        Run labeled queries against one or more backends and compare recall@k, MRR, and latency.
      </p>
      <BenchmarkRunner />
    </section>
  );
}
