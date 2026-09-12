/** Client-side form validation mirroring backend marshmallow rules. */

export function isBase64(value, { minBytes = 0, maxBytes = 4096 } = {}) {
  if (typeof value !== "string" || !value.length) return false;
  const cleaned = value.trim();
  if (cleaned.length % 4 === 1) return false;
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(cleaned)) return false;
  const bytes = Math.floor((cleaned.replace(/=+$/, "").length * 3) / 4);
  return bytes >= minBytes && bytes <= maxBytes;
}

export function validateAesForm({ plaintext, keyB64, mode, demo }) {
  const errors = {};
  if (!plaintext?.trim()) errors.plaintext = "Plaintext is required.";
  else if (plaintext.length > 8192) errors.plaintext = "Max 8192 characters.";
  if (!keyB64?.trim()) errors.key = "Key (base64) is required.";
  else if (!isBase64(keyB64, { minBytes: 16, maxBytes: 32 }))
    errors.key = "Key must be base64 encoding 16, 24, or 32 bytes.";
  if (mode !== "gcm" && !demo)
    errors.mode = `${mode.toUpperCase()} is gallery-only — tick the demo checkbox.`;
  return errors;
}

export function validateLoginForm({ email, password, totpCode }) {
  const errors = {};
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email ?? ""))
    errors.email = "Enter a valid email.";
  if (!password) errors.password = "Password is required.";
  if (totpCode && !/^\d{6}$/.test(totpCode))
    errors.totpCode = "TOTP code is 6 digits.";
  return errors;
}

export function validateRegisterForm({ email, password }) {
  const errors = {};
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email ?? ""))
    errors.email = "Enter a valid email.";
  if (!password || password.length < 8)
    errors.password = "At least 8 characters.";
  return errors;
}

export const hasErrors = (errors) => Object.keys(errors).length > 0;

/** Random key helper for the demos (crypto.getRandomValues — CSPRNG). */
export function randomKeyB64(bytes = 32) {
  const buf = new Uint8Array(bytes);
  crypto.getRandomValues(buf);
  let bin = "";
  buf.forEach((b) => (bin += String.fromCharCode(b)));
  return btoa(bin);
}
