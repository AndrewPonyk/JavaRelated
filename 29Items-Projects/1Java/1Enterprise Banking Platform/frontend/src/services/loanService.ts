import api from './api';
import type {
  LoanApplication,
  LoanApplicationRequest,
  Loan,
  LoanPayment,
  LoanStatistics,
} from '../types/loan';

const LOAN_APPLICATIONS_ENDPOINT = '/loan-applications';
const LOANS_ENDPOINT = '/loans';

export const loanService = {
  // Loan Application endpoints
  async createApplication(request: LoanApplicationRequest): Promise<LoanApplication> {
    const response = await api.post<LoanApplication>(LOAN_APPLICATIONS_ENDPOINT, request);
    return response.data;
  },

  async getApplication(applicationId: string): Promise<LoanApplication> {
    const response = await api.get<LoanApplication>(
      `${LOAN_APPLICATIONS_ENDPOINT}/${applicationId}`
    );
    return response.data;
  },

  async getMyApplications(): Promise<LoanApplication[]> {
    const response = await api.get<LoanApplication[]>(`${LOAN_APPLICATIONS_ENDPOINT}/my`);
    return response.data;
  },

  async cancelApplication(applicationId: string): Promise<LoanApplication> {
    const response = await api.post<LoanApplication>(
      `${LOAN_APPLICATIONS_ENDPOINT}/${applicationId}/cancel`
    );
    return response.data;
  },

  async getApplicationStatistics(): Promise<LoanStatistics> {
    const response = await api.get<LoanStatistics>(`${LOAN_APPLICATIONS_ENDPOINT}/statistics`);
    return response.data;
  },

  // Loan endpoints
  async getMyLoans(): Promise<Loan[]> {
    const response = await api.get<Loan[]>(`${LOANS_ENDPOINT}/my`);
    return response.data;
  },

  async getLoan(loanId: string): Promise<Loan> {
    const response = await api.get<Loan>(`${LOANS_ENDPOINT}/${loanId}`);
    return response.data;
  },

  async getLoanPayments(loanId: string): Promise<LoanPayment[]> {
    const response = await api.get<LoanPayment[]>(`${LOANS_ENDPOINT}/${loanId}/payments`);
    return response.data;
  },

  async makePayment(loanId: string, amount: number): Promise<LoanPayment> {
    const response = await api.post<LoanPayment>(`${LOANS_ENDPOINT}/${loanId}/pay`, { amount });
    return response.data;
  },
};
