// Typed API client. Attaches the bearer token and centralises base-URL config.

import axios, { AxiosInstance } from 'axios';
import type {
  InstanceMeta,
  MlPrediction,
  Series,
  StudyPage,
  StudyQuery,
} from '@/types/dicom';

const baseURL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

export const TOKEN_KEY = 'access_token';

export const http: AxiosInstance = axios.create({ baseURL, timeout: 20000 });

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Bounce to login on auth failure.
http.interceptors.response.use(
  (r) => r,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY);
      if (!window.location.pathname.endsWith('/login')) {
        window.location.assign('/login');
      }
    }
    return Promise.reject(error);
  },
);

export const api = {
  async login(email: string, password: string): Promise<string> {
    // OAuth2 password grant expects form-encoded username/password.
    const form = new URLSearchParams({ username: email, password });
    const { data } = await http.post<{ access_token: string }>('/auth/token', form, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    });
    localStorage.setItem(TOKEN_KEY, data.access_token);
    return data.access_token;
  },

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
  },

  async listStudies(query: StudyQuery = {}): Promise<StudyPage> {
    const { data } = await http.get<StudyPage>('/studies', { params: query });
    return data;
  },

  async getStudySeries(studyInstanceUid: string): Promise<Series[]> {
    const { data } = await http.get<Series[]>(`/studies/${studyInstanceUid}/series`);
    return data;
  },

  async getStudyInstances(studyInstanceUid: string): Promise<InstanceMeta[]> {
    const { data } = await http.get<InstanceMeta[]>(`/studies/${studyInstanceUid}/instances`);
    return data;
  },

  async getFrameUrl(sopInstanceUid: string): Promise<string> {
    const { data } = await http.get<{ url: string }>(`/instances/${sopInstanceUid}/frame-url`);
    return data.url;
  },

  async getMlResults(studyInstanceUid: string): Promise<MlPrediction[]> {
    const { data } = await http.get<MlPrediction[]>(`/ml/results/${studyInstanceUid}`);
    return data;
  },

  async triggerMl(sopInstanceUid: string): Promise<{ status: string }> {
    const { data } = await http.post<{ status: string }>('/ml/trigger', {
      sop_instance_uid: sopInstanceUid,
    });
    return data;
  },
};

export function isAuthenticated(): boolean {
  return Boolean(localStorage.getItem(TOKEN_KEY));
}
