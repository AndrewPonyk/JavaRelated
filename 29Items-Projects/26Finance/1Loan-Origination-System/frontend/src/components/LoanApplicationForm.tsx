import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useLoanApplication } from '../hooks/useLoanApplication';
import { LoanApplicationFormData } from '../types/loan.types';

export const LoanApplicationForm: React.FC = () => {
  const { register, handleSubmit, formState: { errors }, reset } = useForm<LoanApplicationFormData>();
  const { submitApplication, isSubmitting, submitError } = useLoanApplication();
  const [submitSuccess, setSubmitSuccess] = useState(false);

  const onSubmit = async (data: LoanApplicationFormData) => {
    setSubmitSuccess(false);
    submitApplication(data, {
      onSuccess: () => {
        setSubmitSuccess(true);
        reset();
        setTimeout(() => setSubmitSuccess(false), 5000);
      }
    });
  };

  return (
    <div className="loan-application-form">
      <h2>Loan Application</h2>

      {submitSuccess && (
        <div className="success-message">
          Application submitted successfully! You will receive a decision shortly.
        </div>
      )}

      {submitError && (
        <div className="error-message">
          Submission failed: {submitError.message || 'An unexpected error occurred. Please try again.'}
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)}>
        <div className="form-group">
          <label htmlFor="loanAmount">Loan Amount ($)</label>
          <input
            type="number"
            id="loanAmount"
            {...register('loanAmount', {
              required: 'Loan amount is required',
              min: { value: 1000, message: 'Minimum loan amount is $1,000' },
              max: { value: 1000000, message: 'Maximum loan amount is $1,000,000' }
            })}
            placeholder="50000"
          />
          {errors.loanAmount && <span className="error">{errors.loanAmount.message}</span>}
        </div>

        <div className="form-group">
          <label htmlFor="loanPurpose">Loan Purpose</label>
          <select
            id="loanPurpose"
            {...register('loanPurpose', { required: 'Loan purpose is required' })}
          >
            <option value="">Select purpose</option>
            <option value="Home Purchase">Home Purchase</option>
            <option value="Refinance">Refinance</option>
            <option value="Home Improvement">Home Improvement</option>
            <option value="Debt Consolidation">Debt Consolidation</option>
            <option value="Business">Business</option>
          </select>
          {errors.loanPurpose && <span className="error">{errors.loanPurpose.message}</span>}
        </div>

        <div className="form-group">
          <label htmlFor="loanTermMonths">Loan Term (months)</label>
          <select
            id="loanTermMonths"
            {...register('loanTermMonths', {
              required: 'Loan term is required',
              valueAsNumber: true
            })}
          >
            <option value="">Select term</option>
            <option value="120">10 years (120 months)</option>
            <option value="180">15 years (180 months)</option>
            <option value="240">20 years (240 months)</option>
            <option value="360">30 years (360 months)</option>
          </select>
          {errors.loanTermMonths && <span className="error">{errors.loanTermMonths.message}</span>}
        </div>

        <div className="form-group">
          <label htmlFor="applicantId">Applicant ID</label>
          <input
            type="number"
            id="applicantId"
            {...register('applicantId', {
              required: 'Applicant ID is required',
              valueAsNumber: true
            })}
            placeholder="12345"
          />
          {errors.applicantId && <span className="error">{errors.applicantId.message}</span>}
        </div>

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Submitting...' : 'Submit Application'}
        </button>
      </form>

      <style>{`
        .loan-application-form {
          max-width: 600px;
          margin: 2rem auto;
          padding: 2rem;
          border: 1px solid #ddd;
          border-radius: 8px;
        }

        .form-group {
          margin-bottom: 1.5rem;
        }

        label {
          display: block;
          margin-bottom: 0.5rem;
          font-weight: 600;
        }

        input, select {
          width: 100%;
          padding: 0.75rem;
          border: 1px solid #ccc;
          border-radius: 4px;
          font-size: 1rem;
        }

        .error {
          color: #d32f2f;
          font-size: 0.875rem;
          display: block;
          margin-top: 0.25rem;
        }

        button {
          width: 100%;
          padding: 1rem;
          background-color: #1976d2;
          color: white;
          border: none;
          border-radius: 4px;
          font-size: 1rem;
          cursor: pointer;
          font-weight: 600;
        }

        button:hover:not(:disabled) {
          background-color: #1565c0;
        }

        button:disabled {
          background-color: #ccc;
          cursor: not-allowed;
        }

        .success-message {
          background-color: #4caf50;
          color: white;
          padding: 1rem;
          border-radius: 4px;
          margin-bottom: 1.5rem;
        }

        .error-message {
          background-color: #f44336;
          color: white;
          padding: 1rem;
          border-radius: 4px;
          margin-bottom: 1.5rem;
        }
      `}</style>
    </div>
  );
};

export default LoanApplicationForm;
