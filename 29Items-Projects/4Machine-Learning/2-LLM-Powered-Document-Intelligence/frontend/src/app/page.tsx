import DocumentQA from "@/components/DocumentQA";
import DocumentUpload from "@/components/DocumentUpload";

export default function HomePage() {
  return (
    <main className="page">
      <header className="page__header">
        <h1>Document Intelligence</h1>
        <p>Ask questions across your legal &amp; medical documents — answers with citations.</p>
      </header>

      <DocumentUpload />
      <DocumentQA />
    </main>
  );
}
