// Small pure helpers (unit-tested).

/** Format a 0..1 score as a percentage string, e.g. 0.9123 -> "91.2%". */
export function formatScore(score: number): string {
  const clamped = Math.min(1, Math.max(0, score));
  return `${(clamped * 100).toFixed(1)}%`;
}

/** Validate an uploaded image file. Returns an error message or null if valid. */
export function validateImageFile(file: File, maxBytes = 10 * 1024 * 1024): string | null {
  const allowed = ['image/jpeg', 'image/png', 'image/webp'];
  if (!allowed.includes(file.type)) {
    return `Unsupported type: ${file.type || 'unknown'}. Use JPEG, PNG, or WebP.`;
  }
  if (file.size === 0) return 'File is empty.';
  if (file.size > maxBytes) {
    return `File too large (${(file.size / 1_048_576).toFixed(1)} MiB). Max ${(maxBytes / 1_048_576).toFixed(0)} MiB.`;
  }
  return null;
}
