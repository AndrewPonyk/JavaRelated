export type AnalysisStatus = 'queued' | 'running' | 'completed' | 'failed';
export type FindingSeverity = 'info' | 'low' | 'medium' | 'high' | 'critical';

export interface Finding {
  id: string;
  rule_id: string;
  severity: FindingSeverity;
  message: string;
  file_path: string;
  line: number;
  column: number;
  evidence: Record<string, unknown>;
}

export interface Analysis {
  id: string;
  project_name: string;
  source_path: string;
  compile_commands_path?: string | null;
  rule_pack: string;
  status: AnalysisStatus;
  findings: Finding[];
  ast_facts: {
    schemaVersion?: string;
    sourcePath?: string;
    functions?: Array<{ name: string; line: number; column: number }>;
    calls?: Array<{ name: string; line: number; column: number }>;
    variables?: Array<{ name: string; line: number; column: number }>;
    controlFlow?: Array<{ text: string; line: number; column: number }>;
  };
  error_message?: string | null;
  created_at: string;
  updated_at: string;
}

export interface AnalysisListItem {
  id: string;
  project_name: string;
  source_path: string;
  rule_pack: string;
  status: AnalysisStatus;
  finding_count: number;
  highest_severity?: FindingSeverity | null;
  created_at: string;
  updated_at: string;
}

export interface AnalysisCreate {
  project_name: string;
  source_path: string;
  compile_commands_path?: string;
  rule_pack?: string;
  source_code?: string;
}

export interface AnalysisUpdate {
  project_name?: string;
  rule_pack?: string;
  status?: AnalysisStatus;
}

export interface RuleDefinition {
  id: string;
  rule_pack: string;
  severity: FindingSeverity;
  description: string;
  enabled: boolean;
}
