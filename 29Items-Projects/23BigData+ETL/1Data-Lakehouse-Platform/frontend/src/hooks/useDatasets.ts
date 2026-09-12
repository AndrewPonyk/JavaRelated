import type { DatasetLayer, DatasetList } from "../types/dataset";
import { type ApiQuery, useApiQuery } from "./useApiQuery";

/** Fetch the dataset catalog, optionally filtered to one layer. */
export function useDatasets(layer?: DatasetLayer): ApiQuery<DatasetList> {
  const params = new URLSearchParams();
  if (layer) params.set("layer", layer);
  return useApiQuery<DatasetList>(`/api/v1/datasets?${params.toString()}`);
}
