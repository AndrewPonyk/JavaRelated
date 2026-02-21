import React, { useState } from 'react';
import { useDocumentList } from '../hooks/useDocuments';
import documentService from '../services/documentService';
import { LoanDocumentDto } from '../types/document.types';

const DocumentList: React.FC = () => {
  const [applicationId, setApplicationId] = useState<number | ''>('');
  const { data: documents, isLoading, error } = useDocumentList(
    applicationId ? Number(applicationId) : undefined
  );

  const handleDownload = async (doc: LoanDocumentDto) => {
    try {
      const blob = await documentService.downloadDocument(doc.id);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = doc.documentName;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (err) {
      console.error('Download failed:', err);
    }
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  return (
    <div>
      <h3 style={{ color: '#1976d2' }}>Documents</h3>

      <div style={{ marginBottom: '1rem' }}>
        <label style={{ fontWeight: 600, marginRight: '0.5rem' }}>Application ID:</label>
        <input
          type="number"
          value={applicationId}
          onChange={(e) => setApplicationId(e.target.value ? Number(e.target.value) : '')}
          placeholder="Enter application ID to view documents"
          style={{ padding: '0.5rem', border: '1px solid #ccc', borderRadius: '4px', width: '300px' }}
        />
      </div>

      {error && (
        <div style={{ backgroundColor: '#f44336', color: '#fff', padding: '1rem',
                       borderRadius: '4px', marginBottom: '1rem' }}>
          Failed to load documents: {(error as Error).message || 'Unknown error'}
        </div>
      )}

      {isLoading ? (
        <p>Loading documents...</p>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ backgroundColor: '#f5f5f5' }}>
                <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>Name</th>
                <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>Type</th>
                <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>Size</th>
                <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>Uploaded</th>
                <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>Processed</th>
                <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {documents && documents.length > 0 ? (
                documents.map((doc: LoanDocumentDto) => (
                  <tr key={doc.id}>
                    <td style={{ padding: '0.75rem', border: '1px solid #ddd' }}>{doc.documentName}</td>
                    <td style={{ padding: '0.75rem', border: '1px solid #ddd' }}>
                      {doc.documentType.replace(/_/g, ' ')}
                    </td>
                    <td style={{ padding: '0.75rem', border: '1px solid #ddd' }}>
                      {formatFileSize(doc.fileSize)}
                    </td>
                    <td style={{ padding: '0.75rem', border: '1px solid #ddd' }}>
                      {doc.uploadedAt ? new Date(doc.uploadedAt).toLocaleDateString() : 'N/A'}
                    </td>
                    <td style={{ padding: '0.75rem', border: '1px solid #ddd' }}>
                      <span style={{
                        padding: '0.25rem 0.5rem', borderRadius: '4px',
                        backgroundColor: doc.processed ? '#4caf50' : '#ff9800',
                        color: '#fff', fontSize: '0.75rem'
                      }}>
                        {doc.processed ? 'Yes' : 'No'}
                      </span>
                    </td>
                    <td style={{ padding: '0.75rem', border: '1px solid #ddd' }}>
                      <button
                        onClick={() => handleDownload(doc)}
                        style={{
                          padding: '0.25rem 0.75rem', backgroundColor: '#1976d2',
                          color: '#fff', border: 'none', borderRadius: '4px',
                          cursor: 'pointer', fontSize: '0.875rem'
                        }}
                      >
                        Download
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={6} style={{ padding: '2rem', textAlign: 'center', border: '1px solid #ddd' }}>
                    {applicationId ? 'No documents found for this application' : 'Enter an application ID above'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default DocumentList;
