import React, { useState, useEffect, useCallback } from 'react';
import { useForm } from 'react-hook-form';
import apiClient from '../services/api';

interface ApplicantFormData {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  dateOfBirth: string;
  ssn: string;
  annualIncome: number;
  employmentStatus: string;
  employerName: string;
  yearsEmployed: number;
  existingDebt: number;
  numPreviousLoans: number;
  numDelinquencies: number;
}

interface Applicant extends ApplicantFormData {
  id: number;
  createdAt?: string;
}

const ApplicantForm: React.FC = () => {
  const { register, handleSubmit, formState: { errors }, reset } = useForm<ApplicantFormData>();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [createdApplicant, setCreatedApplicant] = useState<Applicant | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [applicants, setApplicants] = useState<Applicant[]>([]);
  const [loadingList, setLoadingList] = useState(true);
  const [listError, setListError] = useState<string | null>(null);

  const fetchApplicants = useCallback(async () => {
    setLoadingList(true);
    setListError(null);
    try {
      const response = await apiClient.get<Applicant[]>('/api/applicants');
      setApplicants(response.data);
    } catch (err: any) {
      setListError(err.response?.data?.message || err.message || 'Failed to load applicants');
    } finally {
      setLoadingList(false);
    }
  }, []);

  useEffect(() => {
    fetchApplicants();
  }, [fetchApplicants]);

  const onSubmit = async (data: ApplicantFormData) => {
    setIsSubmitting(true);
    setSubmitError(null);
    setCreatedApplicant(null);
    try {
      const response = await apiClient.post<Applicant>('/api/applicants', data);
      setCreatedApplicant(response.data);
      reset();
      fetchApplicants();
    } catch (err: any) {
      setSubmitError(err.response?.data?.message || err.message || 'Failed to create applicant');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div>
      <div className="applicant-form">
        <h2>Register Applicant</h2>

        {createdApplicant && (
          <div className="success-message">
            Applicant created! ID: <strong>{createdApplicant.id}</strong> — use this ID in the loan application form.
          </div>
        )}

        {submitError && (
          <div className="error-message">{submitError}</div>
        )}

        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="form-row">
            <div className="form-group">
              <label htmlFor="firstName">First Name *</label>
              <input
                id="firstName"
                {...register('firstName', { required: 'First name is required' })}
                placeholder="John"
              />
              {errors.firstName && <span className="error">{errors.firstName.message}</span>}
            </div>
            <div className="form-group">
              <label htmlFor="lastName">Last Name *</label>
              <input
                id="lastName"
                {...register('lastName', { required: 'Last name is required' })}
                placeholder="Doe"
              />
              {errors.lastName && <span className="error">{errors.lastName.message}</span>}
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label htmlFor="email">Email *</label>
              <input
                type="email"
                id="email"
                {...register('email', {
                  required: 'Email is required',
                  pattern: { value: /^\S+@\S+$/i, message: 'Invalid email' }
                })}
                placeholder="john.doe@example.com"
              />
              {errors.email && <span className="error">{errors.email.message}</span>}
            </div>
            <div className="form-group">
              <label htmlFor="phone">Phone</label>
              <input
                id="phone"
                {...register('phone')}
                placeholder="555-0123"
              />
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label htmlFor="dateOfBirth">Date of Birth</label>
              <input
                type="date"
                id="dateOfBirth"
                {...register('dateOfBirth')}
              />
            </div>
            <div className="form-group">
              <label htmlFor="ssn">SSN</label>
              <input
                id="ssn"
                {...register('ssn', {
                  pattern: { value: /^\d{3}-\d{2}-\d{4}$/, message: 'Format: 123-45-6789' }
                })}
                placeholder="123-45-6789"
              />
              {errors.ssn && <span className="error">{errors.ssn.message}</span>}
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="annualIncome">Annual Income ($) *</label>
            <input
              type="number"
              id="annualIncome"
              {...register('annualIncome', {
                required: 'Annual income is required',
                min: { value: 0, message: 'Must be positive' },
                valueAsNumber: true
              })}
              placeholder="85000"
            />
            {errors.annualIncome && <span className="error">{errors.annualIncome.message}</span>}
          </div>

          <div className="form-row">
            <div className="form-group">
              <label htmlFor="employmentStatus">Employment Status</label>
              <select id="employmentStatus" {...register('employmentStatus')}>
                <option value="">Select status</option>
                <option value="EMPLOYED">Employed</option>
                <option value="SELF_EMPLOYED">Self-Employed</option>
                <option value="UNEMPLOYED">Unemployed</option>
                <option value="RETIRED">Retired</option>
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="employerName">Employer Name</label>
              <input
                id="employerName"
                {...register('employerName')}
                placeholder="Tech Corp"
              />
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="yearsEmployed">Years Employed</label>
            <input
              type="number"
              step="0.5"
              id="yearsEmployed"
              {...register('yearsEmployed', { valueAsNumber: true })}
              placeholder="5"
            />
          </div>

          <div className="form-group">
            <label htmlFor="existingDebt">Existing Debt ($)</label>
            <input
              type="number"
              id="existingDebt"
              {...register('existingDebt', {
                min: { value: 0, message: 'Must be non-negative' },
                valueAsNumber: true
              })}
              placeholder="0"
            />
            {errors.existingDebt && <span className="error">{errors.existingDebt.message}</span>}
          </div>

          <div className="form-row">
            <div className="form-group">
              <label htmlFor="numPreviousLoans">Previous Loans</label>
              <input
                type="number"
                id="numPreviousLoans"
                {...register('numPreviousLoans', {
                  min: { value: 0, message: 'Must be non-negative' },
                  valueAsNumber: true
                })}
                placeholder="0"
              />
              {errors.numPreviousLoans && <span className="error">{errors.numPreviousLoans.message}</span>}
            </div>
            <div className="form-group">
              <label htmlFor="numDelinquencies">Delinquencies</label>
              <input
                type="number"
                id="numDelinquencies"
                {...register('numDelinquencies', {
                  min: { value: 0, message: 'Must be non-negative' },
                  valueAsNumber: true
                })}
                placeholder="0"
              />
              {errors.numDelinquencies && <span className="error">{errors.numDelinquencies.message}</span>}
            </div>
          </div>

          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Creating...' : 'Register Applicant'}
          </button>
        </form>
      </div>

      <div style={{ maxWidth: '900px', margin: '2rem auto' }}>
        <h2>Applicants</h2>

        {listError && (
          <div className="error-message">{listError}</div>
        )}

        {loadingList ? (
          <p>Loading applicants...</p>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ backgroundColor: '#f5f5f5' }}>
                  <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>ID</th>
                  <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>Name</th>
                  <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>Email</th>
                  <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>Phone</th>
                  <th style={{ padding: '0.75rem', textAlign: 'right', border: '1px solid #ddd' }}>Annual Income</th>
                  <th style={{ padding: '0.75rem', textAlign: 'right', border: '1px solid #ddd' }}>Existing Debt</th>
                  <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>Employment</th>
                  <th style={{ padding: '0.75rem', textAlign: 'center', border: '1px solid #ddd' }}>Prev Loans</th>
                  <th style={{ padding: '0.75rem', textAlign: 'center', border: '1px solid #ddd' }}>Delinq.</th>
                  <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>Created</th>
                </tr>
              </thead>
              <tbody>
                {applicants.length > 0 ? (
                  applicants.map((a) => (
                    <tr key={a.id}>
                      <td style={{ padding: '0.75rem', border: '1px solid #ddd', fontWeight: 600 }}>{a.id}</td>
                      <td style={{ padding: '0.75rem', border: '1px solid #ddd' }}>{a.firstName} {a.lastName}</td>
                      <td style={{ padding: '0.75rem', border: '1px solid #ddd' }}>{a.email}</td>
                      <td style={{ padding: '0.75rem', border: '1px solid #ddd' }}>{a.phone || '—'}</td>
                      <td style={{ padding: '0.75rem', border: '1px solid #ddd', textAlign: 'right' }}>
                        {a.annualIncome != null ? `$${Number(a.annualIncome).toLocaleString()}` : '—'}
                      </td>
                      <td style={{ padding: '0.75rem', border: '1px solid #ddd', textAlign: 'right' }}>
                        {a.existingDebt != null ? `$${Number(a.existingDebt).toLocaleString()}` : '—'}
                      </td>
                      <td style={{ padding: '0.75rem', border: '1px solid #ddd' }}>{a.employmentStatus || '—'}</td>
                      <td style={{ padding: '0.75rem', border: '1px solid #ddd', textAlign: 'center' }}>{a.numPreviousLoans ?? '—'}</td>
                      <td style={{ padding: '0.75rem', border: '1px solid #ddd', textAlign: 'center' }}>{a.numDelinquencies ?? '—'}</td>
                      <td style={{ padding: '0.75rem', border: '1px solid #ddd' }}>
                        {a.createdAt ? new Date(a.createdAt).toLocaleDateString() : '—'}
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={10} style={{ padding: '2rem', textAlign: 'center', border: '1px solid #ddd' }}>
                      No applicants registered yet
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <style>{`
        .applicant-form {
          max-width: 600px;
          margin: 2rem auto;
          padding: 2rem;
          border: 1px solid #ddd;
          border-radius: 8px;
        }
        .form-row {
          display: flex;
          gap: 1rem;
        }
        .form-row .form-group {
          flex: 1;
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
          box-sizing: border-box;
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

export default ApplicantForm;
