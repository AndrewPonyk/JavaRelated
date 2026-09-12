/**
 * Data-fetching hooks built on TanStack Query.
 *
 * Using a server-state library (not hand-rolled useEffect) gives caching,
 * request dedupe, retries, and loading/error state for free — the recommended
 * pattern for FHIR data.
 */
import { useQuery } from '@tanstack/react-query';
import { getPatient, searchPatients } from '@/api/patientApi';

export function usePatientSearch(identifier: string) {
  return useQuery({
    queryKey: ['patients', 'search', identifier],
    queryFn: () => searchPatients(identifier),
    enabled: identifier.trim().length > 0, // don't fire on empty input
    staleTime: 30_000,
  });
}

export function usePatient(id: string | null) {
  return useQuery({
    queryKey: ['patients', id],
    queryFn: () => getPatient(id as string),
    enabled: !!id,
  });
}
