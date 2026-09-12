import { describe, expect, it } from "vitest";
import {
  hasErrors,
  isBase64,
  randomKeyB64,
  validateAesForm,
  validateLoginForm,
  validateRegisterForm,
} from "./validation.js";

describe("isBase64", () => {
  it.each([
    ["AAAA", true], // 3 bytes
    ["QUJD", true], // "ABC"
    ["YWJjZA==", true], // padded
    ["", false],
    ["A", false], // length % 4 === 1 — impossible base64
    ["not base64!!", false],
    ["$$$$", false],
  ])("isBase64(%j) === %s", (value, expected) => {
    expect(isBase64(value)).toBe(expected);
  });

  it("accepts exactly 32 bytes", () => {
    const k32 = randomKeyB64(32);
    expect(isBase64(k32, { minBytes: 16, maxBytes: 32 })).toBe(true);
  });

  it("rejects 8 bytes when minBytes=16", () => {
    const k8 = randomKeyB64(8);
    expect(isBase64(k8, { minBytes: 16, maxBytes: 32 })).toBe(false);
  });
});

describe("validateAesForm", () => {
  const key = randomKeyB64(32);

  it("passes for a valid GCM request", () => {
    const errors = validateAesForm({
      plaintext: "attack at dawn",
      keyB64: key,
      mode: "gcm",
      demo: false,
    });
    expect(errors).toEqual({});
    expect(hasErrors(errors)).toBe(false);
  });

  it("rejects empty plaintext and empty key", () => {
    const errors = validateAesForm({
      plaintext: "  ",
      keyB64: "",
      mode: "gcm",
      demo: false,
    });
    expect(errors.plaintext).toMatch(/required/i);
    expect(errors.key).toMatch(/required/i);
    expect(hasErrors(errors)).toBe(true);
  });

  it("rejects keys shorter than 16 bytes", () => {
    const errors = validateAesForm({
      plaintext: "hi",
      keyB64: randomKeyB64(8),
      mode: "gcm",
      demo: false,
    });
    expect(errors.key).toMatch(/16, 24, or 32 bytes/);
  });

  it("blocks CBC/ECB unless the demo checkbox is ticked", () => {
    const blocked = validateAesForm({
      plaintext: "hi",
      keyB64: key,
      mode: "cbc",
      demo: false,
    });
    expect(blocked.mode).toMatch(/gallery-only/i);
    const allowed = validateAesForm({
      plaintext: "hi",
      keyB64: key,
      mode: "cbc",
      demo: true,
    });
    expect(allowed.mode).toBeUndefined();
  });

  it("caps plaintext length at 8192 chars", () => {
    const errors = validateAesForm({
      plaintext: "x".repeat(8193),
      keyB64: key,
      mode: "gcm",
      demo: false,
    });
    expect(errors.plaintext).toMatch(/8192/);
  });
});

describe("validateLoginForm", () => {
  it("passes with email + password", () => {
    expect(validateLoginForm({ email: "a@b.co", password: "secret1" })).toEqual(
      {},
    );
  });

  it("flags bad email, missing password, malformed TOTP", () => {
    const errors = validateLoginForm({
      email: "nope",
      password: "",
      totpCode: "12",
    });
    expect(errors.email).toBeTruthy();
    expect(errors.password).toBeTruthy();
    expect(errors.totpCode).toMatch(/6 digits/);
  });

  it("accepts a 6-digit TOTP code", () => {
    expect(
      validateLoginForm({ email: "a@b.co", password: "x", totpCode: "123456" })
        .totpCode,
    ).toBeUndefined();
  });
});

describe("validateRegisterForm", () => {
  it("requires 8+ character password", () => {
    expect(
      validateRegisterForm({ email: "a@b.co", password: "short" }).password,
    ).toMatch(/8/);
    expect(
      validateRegisterForm({ email: "a@b.co", password: "longenough" })
        .password,
    ).toBeUndefined();
  });

  it("requires a valid email", () => {
    expect(
      validateRegisterForm({ email: "a@b", password: "longenough" }).email,
    ).toBeTruthy();
  });
});

describe("randomKeyB64", () => {
  it("produces distinct CSPRNG output of the right length", () => {
    const a = randomKeyB64(32);
    const b = randomKeyB64(32);
    expect(a).not.toEqual(b); // 2^256 collision would be newsworthy
    expect(atob(a).length).toBe(32);
  });

  it("honours the requested byte count", () => {
    expect(atob(randomKeyB64(16)).length).toBe(16);
    expect(atob(randomKeyB64(24)).length).toBe(24);
  });
});
