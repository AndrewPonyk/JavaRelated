import { DocumentManager } from "@/components/DocumentManager";

export default function DocumentsPage() {
  return (
    <section className="stack">
      <h1>Documents</h1>
      <p className="muted">Ingest documents (they are chunked, embedded, and indexed), then browse or delete them.</p>
      <DocumentManager />
    </section>
  );
}
