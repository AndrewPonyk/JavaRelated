// Data-fetching hook built on React Query: caching, retries, and request
// lifecycle handled for us. Components consume `data/isLoading/error`.
import { useQuery } from "@tanstack/react-query";

import { api } from "../api/client";
import type { ModelVersion } from "../types";

export function useModelVersions(modelName: string) {
  return useQuery<ModelVersion[]>({
    queryKey: ["model-versions", modelName],
    queryFn: () => api.listModelVersions(modelName),
    enabled: modelName.length > 0,
    staleTime: 30_000, // registry metadata changes infrequently
  });
}
