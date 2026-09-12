/** Pure input-validation helpers (unit-tested, UI-agnostic). */

export function normalizeDrugName(raw: string): string {
  return raw.trim().replace(/\s+/g, ' ');
}

export interface AddDrugCheck {
  ok: boolean;
  reason?: string;
}

export function canAddDrug(existing: string[], candidate: string): AddDrugCheck {
  const name = normalizeDrugName(candidate);
  if (!name) {
    return { ok: false, reason: 'Enter a drug name.' };
  }
  if (name.length < 2) {
    return { ok: false, reason: 'Drug name is too short.' };
  }
  if (existing.some((d) => d.toLowerCase() === name.toLowerCase())) {
    return { ok: false, reason: `"${name}" is already in the list.` };
  }
  return { ok: true };
}

export interface DrugListCheck {
  valid: boolean;
  error?: string;
}

export function validateDrugList(drugs: string[]): DrugListCheck {
  if (drugs.length < 2) {
    return { valid: false, error: 'Add at least two drugs to check for interactions.' };
  }
  return { valid: true };
}
