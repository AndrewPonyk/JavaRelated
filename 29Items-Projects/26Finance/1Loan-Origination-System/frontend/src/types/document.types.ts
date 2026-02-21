export interface LoanDocumentDto {
  id: number;
  applicationId: number;
  documentType: string;
  documentName: string;
  fileSize: number;
  mimeType: string;
  uploadedBy?: number;
  uploadedAt: string;
  processed: boolean;
  downloadUrl: string;
}

export interface DocumentSearchResult {
  documentId: number;
  applicationId: number;
  documentType: string;
  documentName: string;
  mimeType: string;
  uploadedAt: string;
  ocrTextSnippet: string;
  score: number;
}

export const DOCUMENT_TYPES = [
  'ID_DOCUMENT',
  'PROOF_OF_INCOME',
  'BANK_STATEMENT',
  'TAX_RETURN',
  'PROPERTY_APPRAISAL',
  'EMPLOYMENT_VERIFICATION',
  'OTHER',
] as const;

export type DocumentType = (typeof DOCUMENT_TYPES)[number];
