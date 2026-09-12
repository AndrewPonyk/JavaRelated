export type Role =
  | 'CREATOR'
  | 'TEAM_LEAD'
  | 'DEPARTMENT_HEAD'
  | 'LEGAL_COUNSEL'
  | 'FINANCE_CONTROLLER'
  | 'EXECUTIVE'
  | 'ADMIN';

export type DocumentStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'IN_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'REVISION_REQUIRED'
  | 'SLA_BREACHED';

export interface ApprovalStep {
  stepId: string;
  tier: number;
  requiredRole: Role;
  assignedTo?: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SKIPPED';
  actionTakenAt?: string | null;
  comments?: string | null;
}

export interface SlaRecord {
  deadline?: string;
  breached: boolean;
  escalatedTo?: string | null;
  warningSent: boolean;
}

export interface NlpAnalysisResult {
  sentimentScore: number;
  polarity: string;
  category: string;
  urgencyScore: number;
  recommendedRole: Role;
  extractedEntities: string[];
}

export interface AuditEntry {
  action: string;
  performedBy: string;
  timestamp: string;
  details: string;
}

export interface DocumentEntity {
  id: string;
  _id?: string;
  title: string;
  content: string;
  status: DocumentStatus;
  creator: {
    userId: string;
    email: string;
    name: string;
    role: Role;
  };
  currentTier: number;
  approvalSteps: ApprovalStep[];
  sla?: SlaRecord;
  nlpAnalysis?: NlpAnalysisResult;
  auditTrail: AuditEntry[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateDocumentPayload {
  title: string;
  content: string;
}

export interface ReviewerActionPayload {
  action: 'APPROVE' | 'REJECT' | 'REQUEST_REVISION';
  comments?: string;
}
