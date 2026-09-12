export function validateLongUrl(value: string): string | null {
  if (!value || value.trim() !== value) return "Enter a URL without surrounding whitespace.";
  if (new TextEncoder().encode(value).length > 2048) return "The URL must be at most 2048 bytes.";
  if (/[\u0000-\u001f\u007f-\u009f]/u.test(value)) {
    return "Control characters are not allowed in URLs.";
  }
  try {
    const parsed = new URL(value);
    if (!["http:", "https:"].includes(parsed.protocol) || !parsed.hostname) {
      return "Enter a complete HTTP or HTTPS URL.";
    }
    if (parsed.username || parsed.password) return "URLs containing credentials are not allowed.";
    return null;
  } catch {
    return "Enter a complete HTTP or HTTPS URL.";
  }
}

export type ShortUrlResponse = {
  short_code: string;
  short_url: string;
  long_url: string;
  visit_count: number;
};

export function parseShortUrlResponse(value: unknown): ShortUrlResponse {
  if (typeof value !== "object" || value === null) throw new Error("The server returned invalid data.");
  const response = value as Record<string, unknown>;
  if (
    typeof response.short_code !== "string" ||
    !/^[A-Za-z0-9_-]{3,32}$/u.test(response.short_code) ||
    typeof response.short_url !== "string" ||
    validateLongUrl(response.short_url) !== null ||
    typeof response.long_url !== "string" ||
    validateLongUrl(response.long_url) !== null ||
    typeof response.visit_count !== "number" ||
    !Number.isFinite(response.visit_count) ||
    response.visit_count < 0
  ) {
    throw new Error("The server returned invalid data.");
  }
  return {
    short_code: response.short_code,
    short_url: response.short_url,
    long_url: response.long_url,
    visit_count: response.visit_count,
  };
}
