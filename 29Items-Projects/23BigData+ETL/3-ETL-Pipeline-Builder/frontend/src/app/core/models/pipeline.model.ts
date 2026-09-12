export type PipelineStatus = 'draft' | 'active' | 'paused';

export interface Pipeline {
  id: string;
  name: string;
  description: string;
  schedule: string; // 5-field cron
  source: string;
  target: string;
  transform_ref: string;
  status: PipelineStatus;
  created_at: string;
  updated_at: string;
}

export interface PipelineCreate {
  name: string;
  description?: string;
  schedule: string;
  source: string;
  target: string;
  transform_ref?: string;
}
