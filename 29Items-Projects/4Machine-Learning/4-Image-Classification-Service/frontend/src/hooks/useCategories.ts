// Hook managing the category taxonomy: list / create / delete with states.
import { useCallback, useEffect, useState } from 'react';

import { createCategory, deleteCategory, listCategories, type Category } from '../api/client';
import { useAuth } from '../auth/AuthContext';

interface UseCategoriesState {
  categories: Category[];
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  add: (name: string, description?: string) => Promise<void>;
  remove: (id: number) => Promise<void>;
}

export function useCategories(): UseCategoriesState {
  const { token } = useAuth();
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setCategories(await listCategories(token ?? undefined));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load categories');
    } finally {
      setLoading(false);
    }
  }, [token]);

  const add = useCallback(
    async (name: string, description?: string) => {
      setError(null);
      try {
        await createCategory({ name, description }, token ?? undefined);
        await refresh();
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to create category');
      }
    },
    [token, refresh],
  );

  const remove = useCallback(
    async (id: number) => {
      setError(null);
      try {
        await deleteCategory(id, token ?? undefined);
        await refresh();
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to delete category');
      }
    },
    [token, refresh],
  );

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { categories, loading, error, refresh, add, remove };
}
