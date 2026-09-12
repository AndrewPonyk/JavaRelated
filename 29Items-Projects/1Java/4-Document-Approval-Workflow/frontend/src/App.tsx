import React, { useState, useEffect, useCallback } from 'react';
import { DocumentEntity, Role, CreateDocumentPayload } from './types/document';
import { api } from './services/api';
import { useEventStream } from './hooks/useEventStream';
import { Header } from './components/Header';
import { DocumentDashboard } from './components/DocumentDashboard';
import { DocumentSubmitModal } from './components/DocumentSubmitModal';

export const App: React.FC = () => {
  const [currentRole, setCurrentRole] = useState<Role>('TEAM_LEAD');
  const [documents, setDocuments] = useState<DocumentEntity[]>([]);
  const [selectedDocId, setSelectedDocId] = useState<string | null>(null);
  const [isSubmitModalOpen, setIsSubmitModalOpen] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 4000);
  };

  const loadDocuments = useCallback(async () => {
    try {
      setIsLoading(true);
      const res = await api.listDocuments();
      if (res && res.documents) {
        setDocuments(res.documents);
        if (!selectedDocId && res.documents.length > 0) {
          setSelectedDocId(res.documents[0].id);
        }
      }
    } catch (err) {
      console.error('Failed to load documents:', err);
      // Fallback demo data if backend is offline
      setDocuments([
        {
          id: 'doc-demo-01',
          title: 'Cloud Infrastructure Vendor Renewal Agreement',
          content: 'Contract renewal for Multi-Region AWS & MongoDB Atlas enterprise cluster. Urgency high due to Q4 discount expiration.',
          status: 'IN_REVIEW',
          creator: {
            userId: 'user-001',
            name: 'Alex Creator',
            email: 'alex@enterprise.io',
            role: 'CREATOR',
          },
          currentTier: 1,
          approvalSteps: [
            { stepId: 's1', tier: 1, requiredRole: 'TEAM_LEAD', status: 'PENDING' },
            { stepId: 's2', tier: 2, requiredRole: 'FINANCE_CONTROLLER', status: 'PENDING' },
            { stepId: 's3', tier: 3, requiredRole: 'EXECUTIVE', status: 'PENDING' },
          ],
          sla: {
            deadline: new Date(Date.now() + 3.5 * 3600 * 1000).toISOString(),
            breached: false,
            warningSent: false,
          },
          nlpAnalysis: {
            sentimentScore: 0.1,
            polarity: 'NEUTRAL',
            category: 'FINANCIAL',
            urgencyScore: 0.65,
            recommendedRole: 'FINANCE_CONTROLLER',
            extractedEntities: ['AWS', 'MongoDB Atlas', 'Q4 Discount'],
          },
          auditTrail: [
            {
              action: 'DOCUMENT_CREATED',
              performedBy: 'Alex Creator',
              timestamp: new Date().toISOString(),
              details: 'Submitted with spaCy classification: FINANCIAL',
            },
          ],
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      ]);
    } finally {
      setIsLoading(false);
    }
  }, [selectedDocId]);

  useEffect(() => {
    api.setUserContext(currentRole, `user-${currentRole.toLowerCase()}`, `${currentRole.replace('_', ' ')} Reviewer`);
  }, [currentRole]);

  useEffect(() => {
    loadDocuments();
  }, [loadDocuments]);

  // Real-time Event Stream Listener
  useEventStream(
    useCallback((msg) => {
      showToast(`Real-time Event: ${msg.eventType} on ${msg.document.title}`);
      setDocuments((prev) => {
        const existingIdx = prev.findIndex((d) => d.id === msg.document.id);
        if (existingIdx >= 0) {
          const updated = [...prev];
          updated[existingIdx] = msg.document;
          return updated;
        } else {
          return [msg.document, ...prev];
        }
      });
    }, [])
  );

  const handleCreateDocument = async (payload: CreateDocumentPayload) => {
    try {
      const created = await api.createDocument(payload);
      showToast(`Document "${created.title}" successfully ingested and auto-routed!`);
      setDocuments((prev) => [created, ...prev]);
      setSelectedDocId(created.id);
    } catch (err: any) {
      showToast(`Error creating document: ${err.message}`);
    }
  };

  const handleReviewerAction = async (
    documentId: string,
    action: 'APPROVE' | 'REJECT' | 'REQUEST_REVISION',
    comments: string
  ) => {
    try {
      const updated = await api.submitAction(documentId, { action, comments });
      showToast(`Decision recorded: ${action} for ${updated.title}`);
      setDocuments((prev) => prev.map((d) => (d.id === updated.id ? updated : d)));
    } catch (err: any) {
      showToast(`Error submitting action: ${err.message}`);
    }
  };

  return (
    <div className="app-container">
      <Header
        currentRole={currentRole}
        onRoleChange={setCurrentRole}
        onOpenSubmit={() => setIsSubmitModalOpen(true)}
      />

      <main className="main-content">
        {toastMessage && (
          <div
            style={{
              position: 'fixed',
              bottom: '2rem',
              right: '2rem',
              background: 'var(--bg-secondary)',
              border: '1px solid var(--accent-primary)',
              borderRadius: 'var(--radius-md)',
              padding: '0.85rem 1.25rem',
              boxShadow: 'var(--shadow-card)',
              color: 'var(--text-main)',
              fontSize: '0.875rem',
              zIndex: 999,
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              animation: 'modal-enter 0.25s ease-out',
            }}
          >
            <span>{toastMessage}</span>
          </div>
        )}

        {isLoading ? (
          <div style={{ textAlign: 'center', padding: '4rem', color: 'var(--text-muted)' }}>
            <div style={{ fontSize: '1.1rem', marginBottom: '0.5rem' }}>Loading documents from Vert.x EventBus...</div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>Connecting to MongoDB reactive client & Quartz SLA engine</div>
          </div>
        ) : (
          <DocumentDashboard
            documents={documents}
            currentRole={currentRole}
            selectedDocId={selectedDocId}
            onSelectDoc={setSelectedDocId}
            onAction={handleReviewerAction}
          />
        )}
      </main>

      <DocumentSubmitModal
        isOpen={isSubmitModalOpen}
        onClose={() => setIsSubmitModalOpen(false)}
        onSubmit={handleCreateDocument}
      />
    </div>
  );
};
