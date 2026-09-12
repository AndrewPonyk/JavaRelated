// Data-fetching hooks built on TanStack Query (caching, retries, states).

import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { toWadoUriImageId } from '@/lib/cornerstone';
import type { MlPrediction, StudyPage, StudyQuery } from '@/types/dicom';

export function useStudies(query: StudyQuery = {}) {
  return useQuery<StudyPage, Error>({
    queryKey: ['studies', query],
    queryFn: () => api.listStudies(query),
    staleTime: 30_000,
  });
}

/** Resolve a study's instances into ordered wadouri imageIds for the viewer. */
export function useStudyImageIds(studyInstanceUid: string | null) {
  return useQuery<string[], Error>({
    queryKey: ['study-images', studyInstanceUid],
    enabled: Boolean(studyInstanceUid),
    queryFn: async () => {
      const instances = await api.getStudyInstances(studyInstanceUid as string);
      const urls = await Promise.all(
        instances.map((i) => api.getFrameUrl(i.sop_instance_uid)),
      );
      return urls.map(toWadoUriImageId);
    },
  });
}

export function useMlResults(studyInstanceUid: string | null) {
  return useQuery<MlPrediction[], Error>({
    queryKey: ['ml-results', studyInstanceUid],
    enabled: Boolean(studyInstanceUid),
    queryFn: () => api.getMlResults(studyInstanceUid as string),
  });
}
