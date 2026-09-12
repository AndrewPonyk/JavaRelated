import React, { useState } from 'react';
import { CreateDocumentPayload } from '../types/document';
import { X, Sparkles, Send, FileCheck } from 'lucide-react';

interface DocumentSubmitModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (payload: CreateDocumentPayload) => Promise<void>;
}

export const DocumentSubmitModal: React.FC<DocumentSubmitModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
}) => {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!isOpen) return null;

  const handleFormSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !content.trim()) return;

    try {
      setIsSubmitting(true);
      await onSubmit({ title, content });
      setTitle('');
      setContent('');
      onClose();
    } finally {
      setIsSubmitting(false);
    }
  };

  const hasUrgentKeywords =
    title.toLowerCase().includes('legal') ||
    content.toLowerCase().includes('lawsuit') ||
    content.toLowerCase().includes('urgent') ||
    content.toLowerCase().includes('contract');

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div
          style={{
            padding: '1.25rem 1.5rem',
            borderBottom: '1px solid var(--border-glass)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <FileCheck size={20} color="var(--accent-primary)" />
            <h3 style={{ fontSize: '1.15rem', fontWeight: 700 }}>Submit Document for Approval</h3>
          </div>
          <button
            onClick={onClose}
            style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
          >
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleFormSubmit} style={{ padding: '1.5rem' }}>
          <div className="form-group">
            <label className="form-label">Document Title</label>
            <input
              type="text"
              className="form-input"
              placeholder="e.g. Master Services Agreement with AWS Infrastructure"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label">Document Body / Context</label>
            <textarea
              className="form-textarea"
              placeholder="Provide document details, budget items, clauses, or operational changes..."
              value={content}
              onChange={(e) => setContent(e.target.value)}
              required
            />
          </div>

          {/* spaCy NLP Auto-Routing Preview Badge */}
          <div
            style={{
              padding: '0.85rem 1rem',
              background: 'rgba(99, 102, 241, 0.08)',
              border: '1px solid var(--border-highlight)',
              borderRadius: 'var(--radius-md)',
              marginBottom: '1.5rem',
              fontSize: '0.825rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.75rem',
            }}
          >
            <Sparkles size={16} color="var(--accent-secondary)" />
            <span>
              {hasUrgentKeywords
                ? 'spaCy Auto-Routing: High-risk keywords detected. Route will be expedited directly to Legal / Executive.'
                : 'spaCy Auto-Routing: Standard routing pipeline will be generated (Team Lead -> Dept Head).'}
            </span>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={isSubmitting}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
              <Send size={15} />
              <span>{isSubmitting ? 'Analyzing & Ingesting...' : 'Submit to EventBus'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
