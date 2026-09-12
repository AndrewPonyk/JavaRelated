// Data hooks for the trials feature (queries + mutations) built on React Query.
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/api/client";
import type { Paginated, Protocol, Study, StudyStatus } from "@/types";

export function useStudies(status?: StudyStatus) {
  const query = status ? `?status=${status}` : "";
  return useQuery({
    queryKey: ["studies", { status }],
    queryFn: () => api.get<Paginated<Study>>(`/studies/${query}`),
    staleTime: 30_000,
  });
}

export function useStudy(id: string | undefined) {
  return useQuery({
    queryKey: ["study", id],
    queryFn: () => api.get<Study>(`/studies/${id}/`),
    enabled: !!id,
  });
}

export function useProtocols() {
  return useQuery({
    queryKey: ["protocols"],
    queryFn: () => api.get<Paginated<Protocol>>("/protocols/"),
  });
}

export function useCreateProtocol() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { code: string; title: string; version: string; phase?: string }) =>
      api.post<Protocol>("/protocols/", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["protocols"] }),
  });
}

export function useCreateStudy() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { protocol: string; name: string; target_enrollment: number }) =>
      api.post<Study>("/studies/", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["studies"] }),
  });
}

export function useOpenStudy() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.post<Study>(`/studies/${id}/open/`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["studies"] });
    },
  });
}

export function useAddArm() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { study: string; name: string }) => api.post("/arms/", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["studies"] }),
  });
}
