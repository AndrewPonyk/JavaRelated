import { useEffect, useState } from 'react';

import { fetchModelManifest, type ModelManifest } from '../services/modelManifest';

type LoadState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'loaded'; readonly manifest: ModelManifest }
  | { readonly kind: 'error'; readonly message: string };

export function ModelStatus() {
  const [state, setState] = useState<LoadState>({ kind: 'loading' });

  useEffect(() => {
    const controller = new AbortController();
    void fetchModelManifest(controller.signal)
      .then((manifest) => setState({ kind: 'loaded', manifest }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setState({
            kind: 'error',
            message: error instanceof Error ? error.message : 'The model status is unavailable.',
          });
        }
      });
    return () => controller.abort();
  }, []);

  if (state.kind === 'loading') {
    return <p className="model-status">Checking auto-crop availability…</p>;
  }
  if (state.kind === 'error') {
    return (
      <p className="model-status model-status--warning" role="status">
        Auto-crop status unavailable. Manual editing still works.
      </p>
    );
  }
  return (
    <p className="model-status" role="status">
      Auto-crop ready ({state.manifest.provider} provider, version {state.manifest.version}).
    </p>
  );
}
