import Link from "next/link";

export default function HomePage() {
  return (
    <section className="stack">
      <h1>Vector Search Platform</h1>
      <p className="muted">
        One API, five backends — an in-memory store plus pgvector, Pinecone, Weaviate, and Milvus.
        Run hybrid semantic search and compare recall@k across backends.
      </p>
      <div className="stack">
        <Link className="nav-link" href="/search">
          → Search documents
        </Link>
        <Link className="nav-link" href="/documents">
          → Manage documents (ingest / list / delete)
        </Link>
        <Link className="nav-link" href="/benchmark">
          → Run a recall@k benchmark
        </Link>
      </div>
    </section>
  );
}
