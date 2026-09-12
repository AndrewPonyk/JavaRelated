export type DbRow = Record<string, unknown>;

export function asString(value: unknown): string {
  return String(value);
}

export function asNullableString(value: unknown): string | null {
  return value == null ? null : String(value);
}

export function asNumber(value: unknown): number {
  return Number(value);
}

export function asBoolean(value: unknown): boolean {
  return Boolean(value);
}

export function asJsonObject(value: unknown): Record<string, unknown> {
  if (value != null && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }

  return {};
}

export function asOptionalJson(value: unknown): unknown {
  return value == null ? null : value;
}
