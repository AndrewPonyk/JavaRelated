import { useState } from "react";

import { DatasetDetail } from "./components/DatasetDetail";
import { DatasetTable } from "./components/DatasetTable";

type View = { page: "list" } | { page: "detail"; datasetId: string };

export default function App() {
  const [view, setView] = useState<View>({ page: "list" });

  return (
    <main className="container">
      <header className="app-header">
        <h1>Lakehouse Console</h1>
        <p>Governed datasets across Bronze / Silver / Gold layers</p>
      </header>

      {view.page === "list" ? (
        <DatasetTable onSelect={(dataset) => setView({ page: "detail", datasetId: dataset.id })} />
      ) : (
        <DatasetDetail datasetId={view.datasetId} onBack={() => setView({ page: "list" })} />
      )}
    </main>
  );
}
