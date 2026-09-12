// Application shell. Provides the React Query client and renders the
// data-scientist console (registry browser shown as the entry view).
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";

import { ModelRegistry } from "./components/ModelRegistry";

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});

export default function App(): JSX.Element {
  const [modelName, setModelName] = useState("churn-classifier");

  return (
    <QueryClientProvider client={queryClient}>
      <main className="console">
        <h1>Enterprise ML Platform</h1>
        <label>
          Model:{" "}
          <input
            value={modelName}
            onChange={(e) => setModelName(e.target.value)}
            placeholder="model name"
          />
        </label>
        <section>
          <h2>Model Registry</h2>
          <ModelRegistry modelName={modelName} />
        </section>
      </main>
    </QueryClientProvider>
  );
}
