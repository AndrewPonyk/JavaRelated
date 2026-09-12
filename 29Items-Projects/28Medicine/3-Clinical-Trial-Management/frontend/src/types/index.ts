// Domain types shared across features.
// In production, generate these from the OpenAPI schema (`npm run gen:api`) to
// prevent drift (TECH-NOTES §3.6). Hand-written here to keep the app self-contained.

export type Role = "SPONSOR" | "PI" | "CRC" | "CDM" | "MONITOR" | "AUDITOR";

export interface User {
  id: number;
  username: string;
  email: string;
  first_name: string;
  last_name: string;
  role: Role;
  mfa_enrolled: boolean;
  is_read_only: boolean;
}

export type StudyStatus = "DRAFT" | "OPEN" | "PAUSED" | "CLOSED" | "COMPLETED";

export interface Arm {
  id: string;
  name: string;
  description: string;
  allocation_ratio: number;
}

export interface VisitTemplate {
  id: string;
  name: string;
  day_offset: number;
  window_before_days: number;
  window_after_days: number;
}

export interface Study {
  id: string;
  protocol: string;
  name: string;
  status: StudyStatus;
  target_enrollment: number;
  planned_start: string | null;
  planned_end: string | null;
  arms: Arm[];
  visit_templates: VisitTemplate[];
  created_at: string;
}

export interface Protocol {
  id: string;
  code: string;
  title: string;
  version: string;
  phase: string;
  summary: string;
  effective_date: string | null;
  criteria: EligibilityCriterion[];
}

export type CriterionType = "INCLUSION" | "EXCLUSION";

export interface EligibilityCriterion {
  id: string;
  protocol: string;
  type: CriterionType;
  order: number;
  text: string;
  coded_rule: Record<string, unknown>;
}

export interface Subject {
  id: string;
  subject_code: string;
  first_name: string;
  last_name: string;
  medical_record_number: string;
  date_of_birth: string;
  sex_at_birth: string;
  age: number | null;
  enrolled: boolean;
  created_at: string;
}

export type EnrollmentStatus =
  | "SCREENING"
  | "CONSENTED"
  | "RANDOMIZED"
  | "ENROLLED"
  | "WITHDRAWN"
  | "SCREEN_FAILED";

export interface Enrollment {
  id: string;
  study: string;
  subject: string;
  subject_code: string;
  arm_name: string | null;
  status: EnrollmentStatus;
  consent_signed_at: string | null;
  randomized_at: string | null;
  baseline_date: string | null;
  withdrawal_reason: string;
  created_at: string;
}

export type ScreeningStatus = "PENDING" | "AWAITING_REVIEW" | "DECIDED" | "FAILED";
export type Decision = "ELIGIBLE" | "INELIGIBLE" | "UNDETERMINED";

export interface RationaleItem {
  criterion_id: string | null;
  type: CriterionType;
  text: string;
  satisfied: boolean | null;
  evidence: string | null;
}

export interface Screening {
  id: string;
  study: string;
  subject: string;
  subject_code: string;
  status: ScreeningStatus;
  ml_score: number | null;
  ml_recommendation: Decision | "";
  rationale: RationaleItem[];
  model_version: string;
  human_decision: Decision | "";
  decided_at: string | null;
  created_at: string;
}

export interface Notification {
  id: number;
  kind: string;
  subject: string;
  body: string;
  entity_type: string;
  entity_id: string;
  read: boolean;
  created_at: string;
}

export interface Paginated<T> {
  count: number;
  next: string | null;
  previous: string | null;
  results: T[];
}
