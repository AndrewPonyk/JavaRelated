import type {
  Analysis,
  AnalysisCreate,
  AnalysisListItem,
  AnalysisStatus,
  AnalysisUpdate,
  Finding,
  FindingSeverity,
  RuleDefinition,
} from '../types/analysis';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
    ...init,
  });

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    try {
      const body = (await response.json()) as { detail?: string };
      message = body.detail ?? message;
    } catch {
      // Preserve the status-based message when the response is not JSON.
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export function listAnalyses(filters?: {
  status?: AnalysisStatus | '';
  severity?: FindingSeverity | '';
}): Promise<AnalysisListItem[]> {
  const params = new URLSearchParams();
  if (filters?.status) params.set('status', filters.status);
  if (filters?.severity) params.set('severity', filters.severity);
  const query = params.toString();
  return request<AnalysisListItem[]>(`/api/analyses${query ? `?${query}` : ''}`);
}

export function getAnalysis(id: string): Promise<Analysis> {
  return request<Analysis>(`/api/analyses/${id}`);
}

export function createAnalysis(payload: AnalysisCreate): Promise<Analysis> {
  return request<Analysis>('/api/analyses', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateAnalysis(id: string, payload: AnalysisUpdate): Promise<Analysis> {
  return request<Analysis>(`/api/analyses/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export function rerunAnalysis(id: string): Promise<Analysis> {
  return request<Analysis>(`/api/analyses/${id}/rerun`, { method: 'POST' });
}

export function deleteAnalysis(id: string): Promise<void> {
  return request<void>(`/api/analyses/${id}`, { method: 'DELETE' });
}

export function listFindings(id: string, severity?: FindingSeverity | ''): Promise<Finding[]> {
  const query = severity ? `?severity=${severity}` : '';
  return request<Finding[]>(`/api/analyses/${id}/findings${query}`);
}

export function listRules(): Promise<RuleDefinition[]> {
  return request<RuleDefinition[]>('/api/rules');
}

export function getSarifUrl(id: string): string {
  return `${API_BASE}/api/analyses/${id}/sarif`;
}
