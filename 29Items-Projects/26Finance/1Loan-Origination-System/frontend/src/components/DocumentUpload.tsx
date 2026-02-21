import React, { useState } from 'react';
import { useDocumentUpload } from '../hooks/useDocuments';
import { DOCUMENT_TYPES } from '../types/document.types';

interface DocumentUploadProps {
  applicationId?: number;
}

const DocumentUpload: React.FC<DocumentUploadProps> = ({ applicationId }) => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [documentType, setDocumentType] = useState<string>('');
  const [appId, setAppId] = useState<number | ''>(applicationId || '');
  const [uploadSuccess, setUploadSuccess] = useState(false);

  const uploadMutation = useDocumentUpload();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFile || !documentType || !appId) return;

    uploadMutation.mutate(
      { applicationId: Number(appId), documentType, file: selectedFile },
      {
        onSuccess: () => {
          setUploadSuccess(true);
          setSelectedFile(null);
          setDocumentType('');
          const fileInput = document.getElementById('fileInput') as HTMLInputElement;
          if (fileInput) fileInput.value = '';
          setTimeout(() => setUploadSuccess(false), 5000);
        },
      }
    );
  };

  return (
    <div className="document-upload">
      <h3>Upload Document</h3>

      {uploadSuccess && (
        <div className="upload-success-message">Document uploaded successfully!</div>
      )}

      {uploadMutation.error && (
        <div className="upload-error-message">
          Upload failed: {(uploadMutation.error as Error).message || 'Unknown error'}
        </div>
      )}

      <form onSubmit={handleSubmit}>
        {!applicationId && (
          <div className="upload-form-group">
            <label htmlFor="appId">Application ID</label>
            <input
              type="number"
              id="appId"
              value={appId}
              onChange={(e) => setAppId(e.target.value ? Number(e.target.value) : '')}
              placeholder="Enter application ID"
              required
            />
          </div>
        )}

        <div className="upload-form-group">
          <label htmlFor="documentType">Document Type</label>
          <select
            id="documentType"
            value={documentType}
            onChange={(e) => setDocumentType(e.target.value)}
            required
          >
            <option value="">Select type</option>
            {DOCUMENT_TYPES.map((type) => (
              <option key={type} value={type}>
                {type.replace(/_/g, ' ')}
              </option>
            ))}
          </select>
        </div>

        <div className="upload-form-group">
          <label htmlFor="fileInput">File (PDF, DOCX, PNG, JPG — max 10MB)</label>
          <input
            type="file"
            id="fileInput"
            accept=".pdf,.docx,.png,.jpg,.jpeg"
            onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
            required
          />
        </div>

        <button type="submit" disabled={uploadMutation.isPending || !selectedFile}>
          {uploadMutation.isPending ? 'Uploading...' : 'Upload Document'}
        </button>
      </form>

      <style>{`
        .document-upload {
          max-width: 600px;
          padding: 1.5rem;
          border: 1px solid #ddd;
          border-radius: 8px;
          margin-bottom: 2rem;
        }
        .document-upload h3 { margin-top: 0; color: #1976d2; }
        .upload-form-group { margin-bottom: 1rem; }
        .upload-form-group label { display: block; margin-bottom: 0.5rem; font-weight: 600; }
        .upload-form-group input, .upload-form-group select {
          width: 100%; padding: 0.75rem; border: 1px solid #ccc;
          border-radius: 4px; font-size: 1rem;
        }
        .document-upload button {
          width: 100%; padding: 0.75rem; background-color: #1976d2;
          color: white; border: none; border-radius: 4px;
          font-size: 1rem; cursor: pointer; font-weight: 600;
        }
        .document-upload button:hover:not(:disabled) { background-color: #1565c0; }
        .document-upload button:disabled { background-color: #ccc; cursor: not-allowed; }
        .upload-success-message {
          background-color: #4caf50; color: white; padding: 1rem;
          border-radius: 4px; margin-bottom: 1rem;
        }
        .upload-error-message {
          background-color: #f44336; color: white; padding: 1rem;
          border-radius: 4px; margin-bottom: 1rem;
        }
      `}</style>
    </div>
  );
};

export default DocumentUpload;
