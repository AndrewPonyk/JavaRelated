export type Severity = 'unknown' | 'minor' | 'moderate' | 'major' | 'contraindicated';

export interface DrugInput {
  rxcui?: string;
  ndc?: string;
  name?: string;
}

export interface Ingredient {
  rxcui: string;
  name: string;
}

export interface Drug {
  rxcui: string;
  name: string;
  tty?: string | null;
  ingredients?: Ingredient[];
  drug_classes?: string[];
}

export interface DrugSearchResult {
  query: string;
  matches: Drug[];
}

export interface InteractionPair {
  rxcui_a: string;
  rxcui_b: string;
  name_a: string;
  name_b: string;
  severity: Severity;
  evidence_level?: string | null;
  mechanism?: string | null;
  description?: string | null;
  source?: string | null;
  ml_predicted: boolean;
  ml_confidence?: number | null;
}

export interface InteractionCheckResponse {
  checked_drugs: string[];
  unresolved: string[];
  interactions: InteractionPair[];
  highest_severity: Severity;
}
