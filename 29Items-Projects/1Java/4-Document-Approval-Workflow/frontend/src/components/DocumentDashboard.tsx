import React, { useState } from 'react';
import { DocumentEntity, Role } from '../types/document';
import { FileText, CheckCircle2, Clock, AlertOctagon, Layers } from 'lucide-react';
import { SlaTimerBadge } from './SlaTimerBadge';
import { SentimentBadge } from './SentimentBadge';
import { ApprovalActionCard } from './ApprovalActionCard';

interface DocumentDashboardProps {
  documents: DocumentEntity[];
  currentRole: Role;
  selectedDocId: string | null;
  onSelectDoc: (id: string) => void;
  onAction: (documentId: string, action: 'APPROVE' | 'REJECT' | 'REQUEST_REVISION', comments: string) => Promise<void>;
}

export const DocumentDashboard: React.FC<DocumentDashboardProps> = ({
  documents,
  currentRole,
  selectedDocId,
  onSelectDoc,
  onAction,
}) => {
  const [filterStatus, setFilterStatus] = useState<string>('ALL');

  // Metrics
  const totalDocs = documents.length;
  const inReviewCount = documents.filter((d) => d.status === 'IN_REVIEW').length;
  const approvedCount = documents.filter((d) => d.status === 'APPROVED').length;
  const breachedCount = documents.filter((d) => d.status === 'SLA_BREACHED' || d.sla?.breached).length;

  const filteredDocs = documents.filter((d) => {
    if (filterStatus === 'ALL') return true;
    if (filterStatus === 'MY_ROLE') {
      const activeStep = d.approvalSteps.find((s) => s.tier === d.currentTier);
      return activeStep?.requiredRole === currentRole && d.status === 'IN_REVIEW';
    }
    return d.status === filterStatus;
  });

  const selectedDocument = documents.find((d) => d.id === selectedDocId);

  return (
    <div>
      {/* Metrics Row */}
      <div className="dashboard-metrics">
        <div className="metric-card">
          <div className="metric-icon-box" style={{ background: 'rgba(99, 102, 241, 0.15)', color: '#818cf8' }}>
            <FileText size={24} />
          </div>
          <div>
            <div className="metric-value">{totalDocs}</div>
            <div className="metric-label">Total Documents</div>
          </div>
        </div>

        <div className="metric-card">
          <div className="metric-icon-box" style={{ background: 'rgba(245, 158, 11, 0.15)', color: '#fbbf24' }}>
            <Clock size={24} />
          </div>
          <div>
            <div className="metric-value">{inReviewCount}</div>
            <div className="metric-label">In Active Review</div>
          </div>
        </div>

        <div className="metric-card">
          <div className="metric-icon-box" style={{ background: 'rgba(16, 185, 129, 0.15)', color: '#34d399' }}>
            <CheckCircle2 size={24} />
          </div>
          <div>
            <div className="metric-value">{approvedCount}</div>
            <div className="metric-label">Approved</div>
          </div>
        </div>

        <div className="metric-card">
          <div className="metric-icon-box" style={{ background: 'rgba(236, 72, 153, 0.15)', color: '#f472b6' }}>
            <AlertOctagon size={24} />
          </div>
          <div>
            <div className="metric-value">{breachedCount}</div>
            <div className="metric-label">SLA Breached</div>
          </div>
        </div>
      </div>

      {/* Filter Tabs */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
          {[
            { id: 'ALL', label: 'All Documents' },
            { id: 'MY_ROLE', label: `Pending My Role (${currentRole})` },
            { id: 'IN_REVIEW', label: 'In Review' },
            { id: 'APPROVED', label: 'Approved' },
            { id: 'SLA_BREACHED', label: 'SLA Breached' },
          ].map((tab) => (
            <button
              key={tab.id}
              className={`btn ${filterStatus === tab.id ? 'btn-primary' : 'btn-secondary'}`}
              style={{ fontSize: '0.8rem', padding: '0.45rem 0.9rem' }}
              onClick={() => setFilterStatus(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Documents Table */}
      <div className="table-container">
        <table className="custom-table">
          <thead>
            <tr>
              <th>Document</th>
              <th>Status</th>
              <th>Current Tier</th>
              <th>spaCy Intelligence</th>
              <th>SLA Tracking</th>
              <th>Creator</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {filteredDocs.length === 0 ? (
              <tr>
                <td colSpan={7} style={{ textAlign: 'center', padding: '2.5rem', color: 'var(--text-muted)' }}>
                  No documents found matching criteria.
                </td>
              </tr>
            ) : (
              filteredDocs.map((doc) => {
                const activeStep = doc.approvalSteps.find((s) => s.tier === doc.currentTier);
                const isSelected = doc.id === selectedDocId;

                return (
                  <tr
                    key={doc.id}
                    onClick={() => onSelectDoc(doc.id)}
                    style={{
                      cursor: 'pointer',
                      background: isSelected ? 'rgba(99, 102, 241, 0.08)' : undefined,
                    }}
                  >
                    <td>
                      <div style={{ fontWeight: 600, color: 'var(--text-main)' }}>{doc.title}</div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>ID: {doc.id}</div>
                    </td>
                    <td>
                      <span className={`badge badge-${doc.status.toLowerCase()}`}>
                        {doc.status.replace('_', ' ')}
                      </span>
                    </td>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                        <Layers size={14} color="var(--accent-primary)" />
                        <span>Tier {doc.currentTier}</span>
                        {activeStep && (
                          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                            ({activeStep.requiredRole})
                          </span>
                        )}
                      </div>
                    </td>
                    <td>
                      <SentimentBadge nlp={doc.nlpAnalysis} />
                    </td>
                    <td>
                      <SlaTimerBadge sla={doc.sla} isTerminal={doc.status === 'APPROVED' || doc.status === 'REJECTED'} />
                    </td>
                    <td>
                      <div style={{ fontSize: '0.85rem' }}>{doc.creator.name}</div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>{doc.creator.role}</div>
                    </td>
                    <td>
                      <button
                        className="btn btn-secondary"
                        style={{ padding: '0.35rem 0.75rem', fontSize: '0.75rem' }}
                        onClick={(e) => {
                          e.stopPropagation();
                          onSelectDoc(doc.id);
                        }}
                      >
                        Inspect
                      </button>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Selected Document Inspection Panel */}
      {selectedDocument && (
        <ApprovalActionCard
          document={selectedDocument}
          currentRole={currentRole}
          onAction={onAction}
        />
      )}
    </div>
  );
};
