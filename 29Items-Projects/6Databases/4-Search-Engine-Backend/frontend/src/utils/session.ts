/**
 * Anonymous session pseudonym sent as X-Session-ID — lets the backend group
 * impressions and clicks for LTR training without any PII.
 */

const STORAGE_KEY = "search-session-id";

let inMemoryId: string | null = null;

function generateId(): string {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `s-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export function getSessionId(): string {
  try {
    let id = localStorage.getItem(STORAGE_KEY);
    if (!id) {
      id = generateId();
      localStorage.setItem(STORAGE_KEY, id);
    }
    return id;
  } catch {
    // Private mode / storage disabled: stable for the tab lifetime at least.
    inMemoryId = inMemoryId ?? generateId();
    return inMemoryId;
  }
}
