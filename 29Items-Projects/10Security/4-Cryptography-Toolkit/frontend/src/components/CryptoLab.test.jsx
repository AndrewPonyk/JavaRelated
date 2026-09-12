import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../api/client.js", () => ({ apiFetch: vi.fn() }));

import CryptoLab from "./CryptoLab.jsx";
import { apiFetch } from "../api/client.js";

const mockFetch = vi.mocked(apiFetch);

beforeEach(() => {
  mockFetch.mockReset();
});

describe("CryptoLab", () => {
  it("renders with a CSPRNG-seeded 32-byte key", () => {
    render(<CryptoLab />);
    expect(screen.getByText(/AES lab/i)).toBeTruthy();
    const key = screen.getByTestId("key").value;
    expect(atob(key).length).toBe(32);
  });

  it("blocks submission on an invalid key and never calls the API", () => {
    render(<CryptoLab />);
    fireEvent.change(screen.getByTestId("key"), {
      target: { value: "not!base64!" },
    });
    fireEvent.click(screen.getByText("Encrypt"));
    expect(screen.getByText(/16, 24, or 32 bytes/)).toBeTruthy();
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("encrypts and shows ciphertext, then decrypts the round trip", async () => {
    mockFetch
      .mockResolvedValueOnce({
        mode: "gcm",
        iv: "aXY=",
        ciphertext: "QUJD",
        tag: "dGFn",
      })
      .mockResolvedValueOnce({ plaintext: "attack at dawn" });

    render(<CryptoLab />);
    fireEvent.click(screen.getByText("Encrypt"));

    await waitFor(() => expect(screen.getByText("QUJD")).toBeTruthy());
    expect(mockFetch).toHaveBeenCalledWith("/aes/encrypt", {
      method: "POST",
      body: expect.objectContaining({
        mode: "gcm",
        plaintext: "attack at dawn",
      }),
    });

    fireEvent.click(screen.getByText("Decrypt"));
    await waitFor(() =>
      expect(screen.getByText("attack at dawn")).toBeTruthy(),
    );
    expect(mockFetch).toHaveBeenLastCalledWith(
      "/aes/decrypt",
      expect.objectContaining({
        body: expect.objectContaining({
          ciphertext_b64: "QUJD",
          tag_b64: "dGFn",
        }),
      }),
    );
  });

  it("flips the first bit of the ciphertext (tamper demo)", async () => {
    mockFetch.mockResolvedValueOnce({
      mode: "gcm",
      iv: "aXY=",
      ciphertext: "QUJD",
      tag: "dGFn",
    });
    render(<CryptoLab />);
    fireEvent.click(screen.getByText("Encrypt"));
    await waitFor(() => expect(screen.getByText("QUJD")).toBeTruthy());

    // 'QUJD' is base64('ABC'); flipping bit 0 of 'A' (0x41^0x01=0x40 '@') yields base64('@BC')
    fireEvent.click(screen.getByText(/Flip 1 bit/i));
    await waitFor(() => expect(screen.queryByText("QUJD")).toBeNull());
    expect(screen.getByText("QEJD")).toBeTruthy();
  });

  it("surfaces API errors as alert text", async () => {
    mockFetch.mockRejectedValueOnce(
      new Error("verification_failed: GCM tag mismatch"),
    );
    render(<CryptoLab />);
    fireEvent.click(screen.getByText("Encrypt"));
    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toMatch(/GCM tag mismatch/);
  });
});
