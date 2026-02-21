import apiClient from './api';
import { LoanDocumentDto, DocumentSearchResult } from '../types/document.types';

export const documentService = {
  uploadDocument: async (
    applicationId: number,
    documentType: string,
    file: File
  ): Promise<LoanDocumentDto> => {
    const formData = new FormData();
    formData.append('applicationId', applicationId.toString());
    formData.append('documentType', documentType);
    formData.append('file', file);

    const response = await apiClient.post<LoanDocumentDto>('/api/documents', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },

  getDocument: async (id: number): Promise<LoanDocumentDto> => {
    const response = await apiClient.get<LoanDocumentDto>(`/api/documents/${id}`);
    return response.data;
  },

  listByApplication: async (applicationId: number): Promise<LoanDocumentDto[]> => {
    const response = await apiClient.get<LoanDocumentDto[]>(
      `/api/documents/application/${applicationId}`
    );
    return response.data;
  },

  downloadDocument: async (id: number): Promise<Blob> => {
    const response = await apiClient.get(`/api/documents/${id}/download`, {
      responseType: 'blob',
    });
    return response.data;
  },

  deleteDocument: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/documents/${id}`);
  },

  searchDocuments: async (
    query: string,
    applicationId?: number
  ): Promise<DocumentSearchResult[]> => {
    const params: Record<string, string | number> = { q: query };
    if (applicationId) params.applicationId = applicationId;
    const response = await apiClient.get<DocumentSearchResult[]>('/api/documents/search', {
      params,
    });
    return response.data;
  },
};

export default documentService;
