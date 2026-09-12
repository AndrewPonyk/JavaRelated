import { useEffect, useState } from 'react';
import { searchProducts } from '@/api/products';
import { ApiError } from '@/api/client';
import type { Product } from '@/types/product';

type SearchState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; items: Product[]; total: number }
  | { status: 'error'; message: string };

/**
 * Debounced product-search hook exposing an explicit state machine
 * (idle | loading | success | error) so the UI can render each case cleanly.
 * Cancels in-flight requests on new input via AbortController.
 */
export function useProductSearch(term: string, debounceMs = 300): SearchState {
  const [state, setState] = useState<SearchState>({ status: 'idle' });

  useEffect(() => {
    if (term.trim() === '') {
      setState({ status: 'idle' });
      return;
    }

    const controller = new AbortController();
    const timer = setTimeout(() => {
      setState({ status: 'loading' });
      searchProducts(term, 1, controller.signal)
        .then((result) => setState({ status: 'success', items: result.items, total: result.total }))
        .catch((error: unknown) => {
          if (controller.signal.aborted) return;
          const message = error instanceof ApiError ? error.message : 'Something went wrong';
          setState({ status: 'error', message });
        });
    }, debounceMs);

    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [term, debounceMs]);

  return state;
}
