import React, { useState } from 'react';
import { useDocumentSearch } from '../hooks/useDocuments';
import { DocumentSearchResult } from '../types/document.types';

const DocumentSearch: React.FC = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const [applicationFilter, setApplicationFilter] = useState<number | ''>('');

  const { data: results, isLoading, error } = useDocumentSearch(
    searchQuery,
    applicationFilter ? Number(applicationFilter) : undefined
  );

  return (
    <div>
      <h3 style={{ color: '#1976d2' }}>Search Documents</h3>

      <div style={{ display: 'flex', gap: '1rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search documents by name, type, or content..."
          style={{ flex: 1, minWidth: '250px', padding: '0.75rem',
                   border: '1px solid #ccc', borderRadius: '4px', fontSize: '1rem' }}
        />
        <input
          type="number"
          value={applicationFilter}
          onChange={(e) => setApplicationFilter(e.target.value ? Number(e.target.value) : '')}
          placeholder="Filter by App ID (optional)"
          style={{ width: '200px', padding: '0.75rem', border: '1px solid #ccc',
                   borderRadius: '4px', fontSize: '1rem' }}
        />
      </div>

      {searchQuery.length > 0 && searchQuery.length < 2 && (
        <p style={{ color: '#9e9e9e' }}>Type at least 2 characters to search</p>
      )}

      {error && (
        <div style={{ backgroundColor: '#f44336', color: '#fff', padding: '1rem',
                       borderRadius: '4px', marginBottom: '1rem' }}>
          Search failed: {(error as Error).message || 'Unknown error'}
        </div>
      )}

      {isLoading && <p>Searching...</p>}

      {results && results.length > 0 && (
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ backgroundColor: '#f5f5f5' }}>
                <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>Name</th>
                <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>Type</th>
                <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>Application</th>
                <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>Uploaded</th>
                <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>Relevance</th>
                <th style={{ padding: '0.75rem', textAlign: 'left', border: '1px solid #ddd' }}>OCR Preview</th>
              </tr>
            </thead>
            <tbody>
              {results.map((result: DocumentSearchResult) => (
                <tr key={result.documentId}>
                  <td style={{ padding: '0.75rem', border: '1px solid #ddd' }}>{result.documentName}</td>
                  <td style={{ padding: '0.75rem', border: '1px solid #ddd' }}>
                    {result.documentType.replace(/_/g, ' ')}
                  </td>
                  <td style={{ padding: '0.75rem', border: '1px solid #ddd' }}>{result.applicationId}</td>
                  <td style={{ padding: '0.75rem', border: '1px solid #ddd' }}>
                    {result.uploadedAt ? new Date(result.uploadedAt).toLocaleDateString() : 'N/A'}
                  </td>
                  <td style={{ padding: '0.75rem', border: '1px solid #ddd' }}>
                    {result.score.toFixed(2)}
                  </td>
                  <td style={{ padding: '0.75rem', border: '1px solid #ddd', maxWidth: '300px',
                               overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {result.ocrTextSnippet || '-'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {results && results.length === 0 && searchQuery.length >= 2 && (
        <p style={{ color: '#9e9e9e', textAlign: 'center', padding: '2rem' }}>
          No documents found for "{searchQuery}"
        </p>
      )}
    </div>
  );
};

export default DocumentSearch;
