import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { InteractionChecker } from './components/InteractionChecker';

const queryClient = new QueryClient();

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <main className="app">
        <InteractionChecker />
      </main>
    </QueryClientProvider>
  );
}
