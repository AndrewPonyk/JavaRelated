import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import documentService from '../services/documentService';

export const useDocumentList = (applicationId?: number) => {
  return useQuery({
    queryKey: ['documents', applicationId],
    queryFn: () => documentService.listByApplication(applicationId!),
    enabled: !!applicationId,
  });
};

export const useDocumentUpload = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      applicationId,
      documentType,
      file,
    }: {
      applicationId: number;
      documentType: string;
      file: File;
    }) => documentService.uploadDocument(applicationId, documentType, file),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['documents', variables.applicationId] });
    },
    onError: (err: Error) => {
      console.error('Document upload failed:', err.message);
    },
  });
};

export const useDocumentDelete = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => documentService.deleteDocument(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
    },
    onError: (err: Error) => {
      console.error('Document delete failed:', err.message);
    },
  });
};

export const useDocumentSearch = (query: string, applicationId?: number) => {
  return useQuery({
    queryKey: ['documentSearch', query, applicationId],
    queryFn: () => documentService.searchDocuments(query, applicationId),
    enabled: query.length >= 2,
  });
};
