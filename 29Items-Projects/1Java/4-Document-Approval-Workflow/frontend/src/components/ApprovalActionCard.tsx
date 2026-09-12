import React, { useState } from 'react';
import { DocumentEntity, Role } from '../types/document';
import { CheckCircle2, XCircle, RotateCcw, Shield, Clock } from 'lucide-react';
import { SlaTimerBadge } from './SlaTimerBadge';
import { SentimentBadge } from './SentimentBadge';

interface ApprovalActionCardProps {
  document: DocumentEntity;
  currentRole: Role;
  onAction: (documentId: string, action: 'APPROVE' | 'REJECT' | 'REQUEST_REVISION', comments: string) => Promise<void>;
}

export const ApprovalActionCard: React.FC<ApprovalActionCardProps> = ({
  document,
  currentRole,
  onAction,
}) => {
  const [comments, setComments] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const currentStep = document.approvalSteps.find((s) => s.tier === document.currentTier);
  const isTerminal = document.status === 'APPROVED' || document.status === 'REJECTED';
  const canReview = !isTerminal && (currentRole === 'ADMIN' || currentRole === currentStep?.requiredRole);

  const handleSubmit = async (action: 'APPROVE' | 'REJECT' | 'REQUEST_REVISION') => {
    try {
      setIsSubmitting(true);
      await onAction(document.id, action, comments);
      setComments('');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="glass-card" style={{ marginTop: '1.5rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.25rem' }}>
        <div>
          <h2 style={{ fontSize: '1.35rem', fontWeight: 700, marginBottom: '0.35rem' }}>{document.title}</h2>
          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
              Submitted by <strong>{document.creator.name}</strong> ({document.creator.role})
            </span>
            <SentimentBadge nlp={document.nlpAnalysis} />
            <SlaTimerBadge sla={document.sla} isTerminal={isTerminal} />
          </div>
        </div>
        <span className={`badge badge-${document.status.toLowerCase()}`}>
          {document.status.replace('_', ' ')}
        </span>
      </div>

      {/* Document Content Box */}
      <div
        style={{
          background: 'rgba(0, 0, 0, 0.25)',
          border: '1px solid var(--border-glass)',
          borderRadius: 'var(--radius-md)',
          padding: '1.25rem',
          fontSize: '0.95rem',
          lineHeight: '1.6',
          marginBottom: '1.5rem',
          whiteSpace: 'pre-wrap',
        }}
      >
        {document.content}
      </div>

      {/* Multi-Tier Workflow Visualizer */}
      <h4 style={{ fontSize: '0.9rem', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '0.75rem' }}>
        Approval Pipeline ({document.approvalSteps.length} Tiers)
      </h4>
      <div style={{ display: 'grid', gridTemplateColumns: `repeat(${document.approvalSteps.length}, 1fr)`, gap: '0.75rem', marginBottom: '1.5rem' }}>
        {document.approvalSteps.map((step) => {
          const isActive = step.tier === document.currentTier && !isTerminal;
          const isDone = step.status === 'APPROVED';
          const isFailed = step.status === 'REJECTED';

          return (
            <div
              key={step.stepId}
              style={{
                background: isActive ? 'rgba(99, 102, 241, 0.15)' : 'rgba(0, 0, 0, 0.2)',
                border: `1px solid ${isActive ? 'var(--accent-primary)' : isDone ? 'var(--status-approved)' : 'var(--border-glass)'}`,
                borderRadius: 'var(--radius-md)',
                padding: '0.85rem',
                position: 'relative',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.35rem' }}>
                <span style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)' }}>
                  TIER {step.tier}
                </span>
                {isDone && <CheckCircle2 size={14} color="var(--status-approved)" />}
                {isFailed && <XCircle size={14} color="var(--status-rejected)" />}
                {isActive && <Clock size={14} color="var(--accent-primary)" />}
              </div>
              <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>{step.requiredRole.replace('_', ' ')}</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', marginTop: '0.2rem' }}>
                {step.assignedTo ? `Reviewed by ${step.assignedTo}` : 'Awaiting reviewer'}
              </div>
              {step.comments && (
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.4rem', fontStyle: 'italic' }}>
                  "{step.comments}"
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Reviewer Action Panel */}
      {canReview ? (
        <div
          style={{
            background: 'rgba(255, 255, 255, 0.02)',
            border: '1px solid var(--border-highlight)',
            borderRadius: 'var(--radius-md)',
            padding: '1.25rem',
          }}
        >
          <h4 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Shield size={16} color="var(--accent-primary)" />
            <span>Review Decision (Active Tier: {currentStep?.requiredRole})</span>
          </h4>
          <textarea
            className="form-textarea"
            placeholder="Add compliance notes, rationale, or revision instructions..."
            value={comments}
            onChange={(e) => setComments(e.target.value)}
            style={{ marginBottom: '1rem', minHeight: '80px' }}
          />
          <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end' }}>
            <button
              className="btn btn-secondary"
              disabled={isSubmitting}
              onClick={() => handleSubmit('REQUEST_REVISION')}
            >
              <RotateCcw size={15} />
              <span>Request Changes</span>
            </button>
            <button
              className="btn btn-danger"
              disabled={isSubmitting}
              onClick={() => handleSubmit('REJECT')}
            >
              <XCircle size={15} />
              <span>Reject</span>
            </button>
            <button
              className="btn btn-success"
              disabled={isSubmitting}
              onClick={() => handleSubmit('APPROVE')}
            >
              <CheckCircle2 size={15} />
              <span>Approve Tier {document.currentTier}</span>
            </button>
          </div>
        </div>
      ) : (
        <div style={{ padding: '1rem', background: 'rgba(0,0,0,0.2)', borderRadius: 'var(--radius-md)', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
          {isTerminal
            ? `Workflow is finalized with status: ${document.status}.`
            : `Active Tier requires ${currentStep?.requiredRole} persona (Your persona: ${currentRole}).`}
        </div>
      )}
    </div>
  );
};
