import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../api/client.js", () => ({ apiFetch: vi.fn() }));

import RsaLab from "./RsaLab.jsx";
import { apiFetch } from "../api/client.js";

const mockFetch = vi.mocked(apiFetch);

const KEYPAIR = {
  bits: 2048,
  e: 65537,
  public_pem:
    "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A\n-----END PUBLIC KEY-----\n",
  private_pem:
    "-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEA\n-----END RSA PRIVATE KEY-----\n",
};

beforeEach(() => {
  mockFetch.mockReset();
});

async function generateKey() {
  mockFetch.mockResolvedValueOnce(KEYPAIR);
  render(<RsaLab />);
  fireEvent.click(screen.getByText("Generate keypair"));
  await screen.findByText(/BEGIN PUBLIC KEY/);
}

describe("RsaLab", () => {
  it("starts without a keypair and generates one", async () => {
    await generateKey();
    expect(mockFetch).toHaveBeenCalledWith("/rsa/keygen", {
      method: "POST",
      body: { bits: 2048 },
    });
    expect(screen.getByText("2048")).toBeTruthy();
  });

  it("runs the OAEP round trip on the default tab", async () => {
    await generateKey();
    mockFetch
      .mockResolvedValueOnce({ ciphertext: "Y3Q=" })
      .mockResolvedValueOnce({ plaintext: "short message" });

    fireEvent.click(screen.getByText(/round trip/i));
    await waitFor(() => expect(screen.getByText("short message")).toBeTruthy());
    expect(mockFetch).toHaveBeenCalledWith(
      "/rsa/encrypt",
      expect.objectContaining({
        body: { public_pem: KEYPAIR.public_pem, plaintext: "short message" },
      }),
    );
    expect(mockFetch).toHaveBeenLastCalledWith(
      "/rsa/decrypt",
      expect.objectContaining({
        body: { private_pem: KEYPAIR.private_pem, ciphertext_b64: "Y3Q=" },
      }),
    );
  });

  it("reports the tampered-signature failure path", async () => {
    await generateKey();
    fireEvent.click(screen.getByRole("tab", { name: /PSS sign\/verify/i }));
    mockFetch
      .mockResolvedValueOnce({ signature: "c2ln" })
      .mockRejectedValueOnce(
        new Error("verification_failed: signature mismatch"),
      );

    fireEvent.click(screen.getByText(/tamper message/i));
    await waitFor(() => expect(screen.getByText("false")).toBeTruthy());
    expect(screen.getByText(/signature mismatch/)).toBeTruthy();
  });

  it("runs the malleability attack from the gallery tab", async () => {
    await generateKey();
    fireEvent.click(screen.getByRole("tab", { name: /Textbook RSA/i }));
    mockFetch.mockResolvedValueOnce({
      deterministic: true,
      original_ciphertext_hex: "aabb".repeat(40),
      attacker_ciphertext_hex: "ccdd".repeat(40),
      decrypted_int: 57005,
      countermeasure: "Always pad — RSA-OAEP.",
    });

    fireEvent.click(screen.getByText(/Run malleability attack/i));
    await waitFor(() =>
      expect(screen.getByText("Always pad — RSA-OAEP.")).toBeTruthy(),
    );
    expect(screen.getByText("true")).toBeTruthy();
    expect(mockFetch).toHaveBeenCalledWith(
      "/rsa/attack/malleability",
      expect.objectContaining({
        body: expect.objectContaining({ demo: true }),
      }),
    );
  });
});
