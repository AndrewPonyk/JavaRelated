import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  useMyLoanApplications,
  useMyLoans,
  useCreateLoanApplication,
  useCancelLoanApplication,
} from '../hooks/useLoans';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorMessage from '../components/common/ErrorMessage';
import { formatCurrency, formatDate, formatPercentage } from '../utils/format';
import type { LoanType, EmploymentStatus, LoanApplication, Loan } from '../types/loan';
import { XMarkIcon } from '@heroicons/react/24/outline';

const loanApplicationSchema = z.object({
  applicantName: z.string().min(2, 'Name must be at least 2 characters'),
  applicantEmail: z.string().email('Please enter a valid email'),
  loanType: z.enum(['PERSONAL', 'MORTGAGE', 'AUTO', 'BUSINESS', 'HOME_EQUITY', 'STUDENT']),
  requestedAmount: z.number().min(1000, 'Minimum loan amount is $1,000'),
  currency: z.string().min(3, 'Currency is required'),
  termMonths: z.number().min(6, 'Minimum term is 6 months').max(360, 'Maximum term is 360 months'),
  annualIncome: z.number().min(1, 'Annual income is required'),
  employmentStatus: z.enum(['EMPLOYED', 'SELF_EMPLOYED', 'UNEMPLOYED', 'RETIRED', 'STUDENT']),
  purpose: z.string().min(10, 'Please provide more details about the loan purpose'),
});

type LoanApplicationFormData = z.infer<typeof loanApplicationSchema>;

const loanTypeInfo = {
  PERSONAL: { name: 'Personal Loan', description: 'Up to $50,000 with competitive rates', apr: '5.99%' },
  MORTGAGE: { name: 'Mortgage', description: 'Home purchase financing', apr: '3.49%' },
  AUTO: { name: 'Auto Loan', description: 'New and used vehicle financing', apr: '3.99%' },
  BUSINESS: { name: 'Business Loan', description: 'Grow your business with flexible terms', apr: '6.99%' },
  HOME_EQUITY: { name: 'Home Equity Line', description: 'Flexible credit backed by your home', apr: '4.49%' },
  STUDENT: { name: 'Student Loan', description: 'Education financing options', apr: '4.99%' },
};

const statusColors: Record<string, string> = {
  DRAFT: 'bg-gray-100 text-gray-800',
  SUBMITTED: 'bg-blue-100 text-blue-800',
  UNDER_REVIEW: 'bg-yellow-100 text-yellow-800',
  DOCUMENTS_REQUIRED: 'bg-orange-100 text-orange-800',
  APPROVED: 'bg-green-100 text-green-800',
  REJECTED: 'bg-red-100 text-red-800',
  DISBURSED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-gray-100 text-gray-800',
  CLOSED: 'bg-gray-100 text-gray-800',
  ACTIVE: 'bg-green-100 text-green-800',
  DELINQUENT: 'bg-red-100 text-red-800',
  PAID_OFF: 'bg-green-100 text-green-800',
};

export default function LoansPage() {
  const { data: applications, isLoading: applicationsLoading, error: applicationsError, refetch: refetchApplications } = useMyLoanApplications();
  const { data: loans, isLoading: loansLoading, error: loansError, refetch: refetchLoans } = useMyLoans();
  const createApplication = useCreateLoanApplication();
  const cancelApplication = useCancelLoanApplication();

  const [showApplicationModal, setShowApplicationModal] = useState(false);
  const [selectedLoanType, setSelectedLoanType] = useState<LoanType | null>(null);
  const [selectedApplication, setSelectedApplication] = useState<LoanApplication | null>(null);
  const [selectedLoan, setSelectedLoan] = useState<Loan | null>(null);
  const [successMessage, setSuccessMessage] = useState('');

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<LoanApplicationFormData>({
    resolver: zodResolver(loanApplicationSchema),
    defaultValues: {
      currency: 'USD',
      termMonths: 36,
      loanType: 'PERSONAL',
    },
  });

  const onSubmit = async (data: LoanApplicationFormData) => {
    try {
      await createApplication.mutateAsync({
        applicantName: data.applicantName,
        applicantEmail: data.applicantEmail,
        loanType: data.loanType as LoanType,
        requestedAmount: data.requestedAmount,
        currency: data.currency,
        termMonths: data.termMonths,
        annualIncome: data.annualIncome,
        employmentStatus: data.employmentStatus as EmploymentStatus,
        purpose: data.purpose,
      });
      setSuccessMessage('Loan application submitted successfully!');
      setShowApplicationModal(false);
      reset();
      setTimeout(() => setSuccessMessage(''), 3000);
    } catch (err) {
      // Error handled by mutation
    }
  };

  const handleCancelApplication = async (applicationId: string) => {
    if (confirm('Are you sure you want to cancel this application?')) {
      try {
        await cancelApplication.mutateAsync(applicationId);
        setSelectedApplication(null);
      } catch (err) {
        // Error handled by mutation
      }
    }
  };

  const startApplication = (loanType: LoanType) => {
    setSelectedLoanType(loanType);
    setValue('loanType', loanType);
    setShowApplicationModal(true);
  };

  const totalOutstanding = loans?.reduce((sum, loan) => sum + loan.outstandingBalance, 0) ?? 0;
  const activeLoans = loans?.filter(l => l.status === 'ACTIVE') ?? [];
  const nextPayment = activeLoans.length > 0
    ? activeLoans.reduce((earliest, loan) =>
        !earliest || (loan.nextPaymentDate && loan.nextPaymentDate < earliest.nextPaymentDate!)
          ? loan
          : earliest
      , null as Loan | null)
    : null;

  const isLoading = applicationsLoading || loansLoading;
  const hasError = applicationsError || loansError;

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (hasError) {
    return (
      <ErrorMessage
        title="Failed to load loans"
        message="Could not load your loan information. Please try again."
        onRetry={() => { refetchApplications(); refetchLoans(); }}
      />
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Loans</h1>
          <p className="text-gray-600 mt-1">
            Manage your loans and apply for new financing
          </p>
        </div>
        <button
          onClick={() => setShowApplicationModal(true)}
          className="btn-primary"
        >
          Apply for a Loan
        </button>
      </div>

      {successMessage && (
        <div className="rounded-lg bg-green-50 p-4">
          <p className="text-sm text-green-700">{successMessage}</p>
        </div>
      )}

      {/* Loan Summary */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="card">
          <p className="text-sm text-gray-500">Active Loans</p>
          <p className="text-3xl font-bold text-gray-900 mt-2">{activeLoans.length}</p>
        </div>

        <div className="card">
          <p className="text-sm text-gray-500">Total Outstanding</p>
          <p className="text-3xl font-bold text-gray-900 mt-2">
            {formatCurrency(totalOutstanding)}
          </p>
        </div>

        <div className="card">
          <p className="text-sm text-gray-500">Next Payment</p>
          <p className="text-3xl font-bold text-gray-900 mt-2">
            {nextPayment ? formatCurrency(nextPayment.nextPaymentAmount ?? 0) : '-'}
          </p>
          {nextPayment?.nextPaymentDate && (
            <p className="text-xs text-gray-500 mt-1">
              Due {formatDate(nextPayment.nextPaymentDate, { month: 'short', day: 'numeric' })}
            </p>
          )}
        </div>
      </div>

      {/* Loan Options */}
      <div className="card">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">
          Available Loan Products
        </h2>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {(Object.entries(loanTypeInfo) as [LoanType, typeof loanTypeInfo.PERSONAL][]).map(([type, info]) => (
            <div
              key={type}
              onClick={() => startApplication(type)}
              className="p-4 border border-gray-200 rounded-lg hover:border-primary-500 cursor-pointer transition-colors"
            >
              <h3 className="font-medium text-gray-900">{info.name}</h3>
              <p className="text-sm text-gray-500 mt-1">{info.description}</p>
              <p className="text-sm text-primary-600 mt-2">Starting at {info.apr} APR</p>
            </div>
          ))}
        </div>
      </div>

      {/* Active Loans */}
      <div className="card">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">
          Your Loans
        </h2>

        {loans && loans.length > 0 ? (
          <div className="divide-y divide-gray-100">
            {loans.map((loan) => (
              <div
                key={loan.id}
                onClick={() => setSelectedLoan(loan)}
                className="py-4 flex items-center justify-between hover:bg-gray-50 px-2 rounded-lg cursor-pointer"
              >
                <div>
                  <p className="font-medium text-gray-900">
                    {loanTypeInfo[loan.loanType]?.name || loan.loanType}
                  </p>
                  <p className="text-sm text-gray-500">
                    {loan.loanNumber} &middot; {loan.termMonths} months
                  </p>
                </div>
                <div className="text-right">
                  <p className="font-semibold text-gray-900">
                    {formatCurrency(loan.outstandingBalance)}
                  </p>
                  <span className={`badge ${statusColors[loan.status] || 'bg-gray-100 text-gray-800'}`}>
                    {loan.status.replace('_', ' ')}
                  </span>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-center py-8 text-gray-500">
            <p>You don't have any active loans</p>
          </div>
        )}
      </div>

      {/* Pending Applications */}
      {applications && applications.length > 0 && (
        <div className="card">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">
            Your Applications
          </h2>

          <div className="divide-y divide-gray-100">
            {applications.map((app) => (
              <div
                key={app.id}
                onClick={() => setSelectedApplication(app)}
                className="py-4 flex items-center justify-between hover:bg-gray-50 px-2 rounded-lg cursor-pointer"
              >
                <div>
                  <p className="font-medium text-gray-900">
                    {loanTypeInfo[app.loanType]?.name || app.loanType}
                  </p>
                  <p className="text-sm text-gray-500">
                    {app.applicationNumber} &middot; {formatDate(app.createdAt, { month: 'short', day: 'numeric', year: 'numeric' })}
                  </p>
                </div>
                <div className="text-right">
                  <p className="font-semibold text-gray-900">
                    {formatCurrency(app.requestedAmount)}
                  </p>
                  <span className={`badge ${statusColors[app.status] || 'bg-gray-100 text-gray-800'}`}>
                    {app.status.replace('_', ' ')}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Application Modal */}
      {showApplicationModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="card w-full max-w-lg max-h-[90vh] overflow-y-auto">
            <div className="flex justify-between items-start mb-4">
              <h2 className="text-xl font-semibold">
                {selectedLoanType ? `Apply for ${loanTypeInfo[selectedLoanType].name}` : 'Loan Application'}
              </h2>
              <button
                onClick={() => {
                  setShowApplicationModal(false);
                  setSelectedLoanType(null);
                  reset();
                }}
                className="p-2 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100"
              >
                <XMarkIcon className="w-5 h-5" />
              </button>
            </div>

            {createApplication.error && (
              <div className="mb-4 rounded-lg bg-red-50 p-4">
                <p className="text-sm text-red-700">Failed to submit application. Please try again.</p>
              </div>
            )}

            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Full Name
                  </label>
                  <input
                    {...register('applicantName')}
                    className="input"
                    placeholder="John Doe"
                  />
                  {errors.applicantName && (
                    <p className="mt-1 text-sm text-red-600">{errors.applicantName.message}</p>
                  )}
                </div>

                <div className="col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Email Address
                  </label>
                  <input
                    {...register('applicantEmail')}
                    type="email"
                    className="input"
                    placeholder="john@example.com"
                  />
                  {errors.applicantEmail && (
                    <p className="mt-1 text-sm text-red-600">{errors.applicantEmail.message}</p>
                  )}
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Loan Type
                  </label>
                  <select {...register('loanType')} className="input">
                    <option value="PERSONAL">Personal Loan</option>
                    <option value="MORTGAGE">Mortgage</option>
                    <option value="AUTO">Auto Loan</option>
                    <option value="BUSINESS">Business Loan</option>
                    <option value="HOME_EQUITY">Home Equity</option>
                    <option value="STUDENT">Student Loan</option>
                  </select>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Currency
                  </label>
                  <select {...register('currency')} className="input">
                    <option value="USD">USD</option>
                    <option value="EUR">EUR</option>
                    <option value="GBP">GBP</option>
                  </select>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Loan Amount
                  </label>
                  <div className="relative">
                    <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500">$</span>
                    <input
                      {...register('requestedAmount', { valueAsNumber: true })}
                      type="number"
                      className="input pl-8"
                      placeholder="25000"
                    />
                  </div>
                  {errors.requestedAmount && (
                    <p className="mt-1 text-sm text-red-600">{errors.requestedAmount.message}</p>
                  )}
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Term (Months)
                  </label>
                  <select {...register('termMonths', { valueAsNumber: true })} className="input">
                    <option value={12}>12 months</option>
                    <option value={24}>24 months</option>
                    <option value={36}>36 months</option>
                    <option value={48}>48 months</option>
                    <option value={60}>60 months</option>
                    <option value={84}>84 months</option>
                    <option value={120}>120 months</option>
                    <option value={180}>180 months</option>
                    <option value={240}>240 months</option>
                    <option value={360}>360 months</option>
                  </select>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Annual Income
                  </label>
                  <div className="relative">
                    <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500">$</span>
                    <input
                      {...register('annualIncome', { valueAsNumber: true })}
                      type="number"
                      className="input pl-8"
                      placeholder="75000"
                    />
                  </div>
                  {errors.annualIncome && (
                    <p className="mt-1 text-sm text-red-600">{errors.annualIncome.message}</p>
                  )}
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Employment Status
                  </label>
                  <select {...register('employmentStatus')} className="input">
                    <option value="EMPLOYED">Employed</option>
                    <option value="SELF_EMPLOYED">Self-Employed</option>
                    <option value="RETIRED">Retired</option>
                    <option value="STUDENT">Student</option>
                    <option value="UNEMPLOYED">Unemployed</option>
                  </select>
                </div>

                <div className="col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Purpose of Loan
                  </label>
                  <textarea
                    {...register('purpose')}
                    rows={3}
                    className="input"
                    placeholder="Describe what you plan to use the loan for..."
                  />
                  {errors.purpose && (
                    <p className="mt-1 text-sm text-red-600">{errors.purpose.message}</p>
                  )}
                </div>
              </div>

              <div className="flex justify-end space-x-3 pt-4">
                <button
                  type="button"
                  onClick={() => {
                    setShowApplicationModal(false);
                    setSelectedLoanType(null);
                    reset();
                  }}
                  className="btn-secondary"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting || createApplication.isPending}
                  className="btn-primary"
                >
                  {isSubmitting || createApplication.isPending ? 'Submitting...' : 'Submit Application'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Application Detail Modal */}
      {selectedApplication && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="card w-full max-w-md">
            <div className="flex justify-between items-start mb-4">
              <h2 className="text-xl font-semibold">Application Details</h2>
              <button
                onClick={() => setSelectedApplication(null)}
                className="p-2 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100"
              >
                <XMarkIcon className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-4">
              <div className="flex justify-between items-center">
                <span className="text-gray-500">Application #</span>
                <span className="font-mono">{selectedApplication.applicationNumber}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-gray-500">Status</span>
                <span className={`badge ${statusColors[selectedApplication.status]}`}>
                  {selectedApplication.status.replace('_', ' ')}
                </span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-gray-500">Loan Type</span>
                <span>{loanTypeInfo[selectedApplication.loanType]?.name}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-gray-500">Requested Amount</span>
                <span className="font-semibold">{formatCurrency(selectedApplication.requestedAmount)}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-gray-500">Term</span>
                <span>{selectedApplication.termMonths} months</span>
              </div>
              {selectedApplication.creditScore && (
                <div className="flex justify-between items-center">
                  <span className="text-gray-500">Credit Score</span>
                  <span>{selectedApplication.creditScore}</span>
                </div>
              )}
              {selectedApplication.approvedAmount && (
                <div className="flex justify-between items-center">
                  <span className="text-gray-500">Approved Amount</span>
                  <span className="font-semibold text-green-600">
                    {formatCurrency(selectedApplication.approvedAmount)}
                  </span>
                </div>
              )}
              {selectedApplication.interestRate && (
                <div className="flex justify-between items-center">
                  <span className="text-gray-500">Interest Rate</span>
                  <span>{formatPercentage(selectedApplication.interestRate)}</span>
                </div>
              )}
              {selectedApplication.rejectionReason && (
                <div className="bg-red-50 rounded-lg p-3">
                  <p className="text-sm text-red-700">{selectedApplication.rejectionReason}</p>
                </div>
              )}
              <div className="flex justify-between items-center">
                <span className="text-gray-500">Submitted</span>
                <span>{formatDate(selectedApplication.createdAt)}</span>
              </div>

              {['SUBMITTED', 'UNDER_REVIEW', 'DOCUMENTS_REQUIRED'].includes(selectedApplication.status) && (
                <button
                  onClick={() => handleCancelApplication(selectedApplication.id)}
                  disabled={cancelApplication.isPending}
                  className="w-full btn-danger mt-4"
                >
                  {cancelApplication.isPending ? 'Cancelling...' : 'Cancel Application'}
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Loan Detail Modal */}
      {selectedLoan && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="card w-full max-w-md">
            <div className="flex justify-between items-start mb-4">
              <h2 className="text-xl font-semibold">Loan Details</h2>
              <button
                onClick={() => setSelectedLoan(null)}
                className="p-2 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100"
              >
                <XMarkIcon className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-4">
              <div className="bg-gradient-to-r from-primary-600 to-primary-700 rounded-xl p-6 text-white">
                <p className="text-sm opacity-80">Outstanding Balance</p>
                <p className="text-3xl font-bold mt-2">
                  {formatCurrency(selectedLoan.outstandingBalance)}
                </p>
                <div className="mt-4 flex items-center justify-between">
                  <span className={`badge ${statusColors[selectedLoan.status]} text-xs`}>
                    {selectedLoan.status.replace('_', ' ')}
                  </span>
                  <span className="text-sm opacity-80">{loanTypeInfo[selectedLoan.loanType]?.name}</span>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <p className="text-gray-500">Loan Number</p>
                  <p className="font-mono">{selectedLoan.loanNumber}</p>
                </div>
                <div>
                  <p className="text-gray-500">Principal</p>
                  <p className="font-medium">{formatCurrency(selectedLoan.principalAmount)}</p>
                </div>
                <div>
                  <p className="text-gray-500">Interest Rate</p>
                  <p className="font-medium">{formatPercentage(selectedLoan.interestRate)}</p>
                </div>
                <div>
                  <p className="text-gray-500">Monthly Payment</p>
                  <p className="font-medium">{formatCurrency(selectedLoan.monthlyPayment)}</p>
                </div>
                <div>
                  <p className="text-gray-500">Term</p>
                  <p className="font-medium">{selectedLoan.termMonths} months</p>
                </div>
                {selectedLoan.maturityDate && (
                  <div>
                    <p className="text-gray-500">Maturity Date</p>
                    <p className="font-medium">
                      {formatDate(selectedLoan.maturityDate, { year: 'numeric', month: 'short', day: 'numeric' })}
                    </p>
                  </div>
                )}
              </div>

              {selectedLoan.nextPaymentDate && selectedLoan.status === 'ACTIVE' && (
                <div className="bg-yellow-50 rounded-lg p-4">
                  <p className="text-sm text-yellow-800">
                    Next payment of <strong>{formatCurrency(selectedLoan.nextPaymentAmount ?? 0)}</strong> due on{' '}
                    <strong>{formatDate(selectedLoan.nextPaymentDate, { month: 'long', day: 'numeric', year: 'numeric' })}</strong>
                  </p>
                </div>
              )}

              {selectedLoan.status === 'ACTIVE' && (
                <button className="w-full btn-primary">
                  Make a Payment
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
