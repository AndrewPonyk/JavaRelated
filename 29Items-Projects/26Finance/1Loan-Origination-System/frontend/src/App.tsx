import React, { useState } from 'react';
import LoanApplicationForm from './components/LoanApplicationForm';
import DocumentUpload from './components/DocumentUpload';
import DocumentList from './components/DocumentList';
import DocumentSearch from './components/DocumentSearch';
import { useAllLoanApplications } from './hooks/useLoanApplication';
import { LoanApplication } from './types/loan.types';

const statusBadgeColor = (status?: string): string => {
  switch (status) {
    case 'APPROVED':
      return '#4caf50';
    case 'REJECTED':
      return '#f44336';
    case 'UNDER_REVIEW':
      return '#ff9800';
    case 'MANUAL_REVIEW_REQUIRED':
      return '#9c27b0';
    case 'FUNDED':
      return '#1976d2';
    default:
      return '#9e9e9e';
  }
};

const App: React.FC = () => {
  const [activeView, setActiveView] = useState<'apply' | 'dashboard' | 'documents'>('apply');
  const { data: applications, isLoading, error } = useAllLoanApplications();

  return (
    <div style={{ fontFamily: 'sans-serif', maxWidth: '1200px', margin: '0 auto', padding: '2rem' }}>
      <header style={{ borderBottom: '2px solid #1976d2', paddingBottom: '1rem', marginBottom: '2rem' }}>
        <h1 style={{ margin: 0, color: '#1976d2' }}>Loan Origination System</h1>
        <nav style={{ marginTop: '1rem' }}>
          <button
            onClick={() => setActiveView('apply')}
            style={{
              padding: '0.5rem 1rem',
              marginRight: '1rem',
              backgroundColor: activeView === 'apply' ? '#1976d2' : '#fff',
              color: activeView === 'apply' ? '#fff' : '#1976d2',
              border: '1px solid #1976d2',
              borderRadius: '4px',
              cursor: 'pointer'
            }}
          >
            Apply for Loan
          </button>
          <button
            onClick={() => setActiveView('dashboard')}
            style={{
              padding: '0.5rem 1rem',
              backgroundColor: activeView === 'dashboard' ? '#1976d2' : '#fff',
              color: activeView === 'dashboard' ? '#fff' : '#1976d2',
              border: '1px solid #1976d2',
              borderRadius: '4px',
              cursor: 'pointer'
            }}
          >
            Application Dashboard
          </button>
          <button
            onClick={() => setActiveView('documents')}
            style={{
              padding: '0.5rem 1rem',
              marginRight: '1rem',
              backgroundColor: activeView === 'documents' ? '#1976d2' : '#fff',
              color: activeView === 'documents' ? '#fff' : '#1976d2',
              border: '1px solid #1976d2',
              borderRadius: '4px',
              cursor: 'pointer'
            }}
          >
            Documents
          </button>
        </nav>
      </header>

      <main>
        {activeView === 'apply' && <LoanApplicationForm />}

        {activeView === 'documents' && (
          <div>
            <DocumentUpload />
            <DocumentSearch />
            <DocumentList />
          </div>
        )}

        {activeView === 'dashboard' && (
          <div>
            <h2>Application Dashboard</h2>

            {error && (
              <div style={{
                backgroundColor: '#f44336',
                color: '#fff',
                padding: '1rem',
                borderRadius: '4px',
                marginBottom: '1rem'
              }}>
                Failed to load applications: {(error as Error).message || 'Unknown error'}
              </div>
            )}

            {isLoading ? (
              <p>Loading applications...</p>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr style={{ backgroundColor: '#f5f5f5' }}>
                      <th style={{ padding: '1rem', textAlign: 'left', border: '1px solid #ddd' }}>Application ID</th>
                      <th style={{ padding: '1rem', textAlign: 'left', border: '1px solid #ddd' }}>Loan Amount</th>
                      <th style={{ padding: '1rem', textAlign: 'left', border: '1px solid #ddd' }}>Purpose</th>
                      <th style={{ padding: '1rem', textAlign: 'left', border: '1px solid #ddd' }}>Status</th>
                      <th style={{ padding: '1rem', textAlign: 'left', border: '1px solid #ddd' }}>Credit Score</th>
                      <th style={{ padding: '1rem', textAlign: 'left', border: '1px solid #ddd' }}>Created</th>
                    </tr>
                  </thead>
                  <tbody>
                    {applications && applications.length > 0 ? (
                      applications.map((app: LoanApplication) => (
                        <tr key={app.id}>
                          <td style={{ padding: '1rem', border: '1px solid #ddd' }}>{app.applicationId}</td>
                          <td style={{ padding: '1rem', border: '1px solid #ddd' }}>
                            ${app.loanAmount?.toLocaleString()}
                          </td>
                          <td style={{ padding: '1rem', border: '1px solid #ddd' }}>{app.loanPurpose}</td>
                          <td style={{ padding: '1rem', border: '1px solid #ddd' }}>
                            <span style={{
                              padding: '0.25rem 0.5rem',
                              borderRadius: '4px',
                              backgroundColor: statusBadgeColor(app.status),
                              color: '#fff',
                              fontSize: '0.875rem'
                            }}>
                              {app.status}
                            </span>
                          </td>
                          <td style={{ padding: '1rem', border: '1px solid #ddd' }}>
                            {app.creditScore || 'N/A'}
                          </td>
                          <td style={{ padding: '1rem', border: '1px solid #ddd' }}>
                            {app.createdAt ? new Date(app.createdAt).toLocaleDateString() : 'N/A'}
                          </td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td colSpan={6} style={{ padding: '2rem', textAlign: 'center', border: '1px solid #ddd' }}>
                          No applications found
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </main>

    </div>
  );
};

export default App;
