import { DocumentEntity, CreateDocumentPayload, ReviewerActionPayload, Role } from '../types/document';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api/v1';

export class WorkflowApiClient {
  private currentRole: Role = 'CREATOR';
  private currentUserId: string = 'user-alex';
  private currentUserName: string = 'Alex Creator';

  public setUserContext(role: Role, userId: string, name: string) {
    this.currentRole = role;
    this.currentUserId = userId;
    this.currentUserName = name;
  }

  private getHeaders(): HeadersInit {
    return {
      'Content-Type': 'application/json',
      'X-User-Role': this.currentRole,
      'X-User-Id': this.currentUserId,
      'X-User-Name': this.currentUserName,
    };
  }

  async listDocuments(status?: string, role?: string): Promise<{ documents: DocumentEntity[]; total: number }> {
    const params = new URLSearchParams();
    if (status) params.append('status', status);
    if (role) params.append('role', role);

    const res = await fetch(`${API_BASE}/documents?${params.toString()}`, {
      method: 'GET',
      headers: this.getHeaders(),
    });

    if (!res.ok) {
      throw new Error(`Failed to fetch documents: ${res.statusText}`);
    }
    return res.json();
  }

  async getDocument(id: string): Promise<DocumentEntity> {
    const res = await fetch(`${API_BASE}/documents/${id}`, {
      method: 'GET',
      headers: this.getHeaders(),
    });

    if (!res.ok) {
      throw new Error(`Failed to get document: ${res.statusText}`);
    }
    return res.json();
  }

  async createDocument(payload: CreateDocumentPayload): Promise<DocumentEntity> {
    const res = await fetch(`${API_BASE}/documents`, {
      method: 'POST',
      headers: this.getHeaders(),
      body: JSON.stringify(payload),
    });

    if (!res.ok) {
      throw new Error(`Failed to create document: ${res.statusText}`);
    }
    return res.json();
  }

  async submitAction(documentId: string, payload: ReviewerActionPayload): Promise<DocumentEntity> {
    const res = await fetch(`${API_BASE}/documents/${documentId}/action`, {
      method: 'POST',
      headers: this.getHeaders(),
      body: JSON.stringify(payload),
    });

    if (!res.ok) {
      throw new Error(`Failed to perform action: ${res.statusText}`);
    }
    return res.json();
  }
}

export const api = new WorkflowApiClient();
