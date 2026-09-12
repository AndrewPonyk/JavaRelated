import { useEffect, useState } from 'react';

import { useQuery } from '@tanstack/react-query';

import { searchDrugs } from '../api/client';

/** Debounce a rapidly-changing value (e.g. a text input). */
export function useDebounced<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(id);
  }, [value, delayMs]);
  return debounced;
}

/** Live, debounced drug-name search against the backend (RxNorm/graph). */
export function useDrugSearch(term: string) {
  const debounced = useDebounced(term.trim(), 300);
  return useQuery({
    queryKey: ['drug-search', debounced],
    queryFn: () => searchDrugs(debounced),
    enabled: debounced.length >= 2,
    staleTime: 60_000,
  });
}
