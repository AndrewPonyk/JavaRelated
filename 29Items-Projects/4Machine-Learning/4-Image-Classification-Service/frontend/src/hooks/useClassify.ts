// Data-fetching hook encapsulating loading / error / success states.
import { useCallback, useState } from 'react';

import { classifyImage, type ClassifyResponse } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { validateImageFile } from '../utils/format';

interface UseClassifyState {
  data: ClassifyResponse | null;
  loading: boolean;
  error: string | null;
  classify: (file: File) => Promise<void>;
  reset: () => void;
}

export function useClassify(): UseClassifyState {
  const { token } = useAuth();
  const [data, setData] = useState<ClassifyResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const classify = useCallback(
    async (file: File) => {
      const validationError = validateImageFile(file);
      if (validationError) {
        setError(validationError);
        return;
      }
      setLoading(true);
      setError(null);
      setData(null);
      try {
        setData(await classifyImage(file, token ?? undefined));
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Unknown error');
      } finally {
        setLoading(false);
      }
    },
    [token],
  );

  const reset = useCallback(() => {
    setData(null);
    setError(null);
    setLoading(false);
  }, []);

  return { data, loading, error, classify, reset };
}
