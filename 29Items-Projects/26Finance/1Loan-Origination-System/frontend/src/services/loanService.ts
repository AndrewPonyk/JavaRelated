import apiClient from './api';
import { LoanApplication, LoanApplicationFormData } from '../types/loan.types';

export const loanService = {
  submitApplication: async (data: LoanApplicationFormData): Promise<LoanApplication> => {
    const response = await apiClient.post<LoanApplication>('/api/applications', data);
    return response.data;
  },

  getApplication: async (id: number): Promise<LoanApplication> => {
    const response = await apiClient.get<LoanApplication>(`/api/applications/${id}`);
    return response.data;
  },

  getAllApplications: async (status?: string): Promise<LoanApplication[]> => {
    const params = status ? { status } : {};
    const response = await apiClient.get<LoanApplication[]>('/api/applications', { params });
    return response.data;
  },

  updateApplicationStatus: async (id: number, status: string): Promise<LoanApplication> => {
    const response = await apiClient.put<LoanApplication>(
      `/api/applications/${id}/status`,
      null,
      { params: { status } }
    );
    return response.data;
  }
};

export default loanService;
