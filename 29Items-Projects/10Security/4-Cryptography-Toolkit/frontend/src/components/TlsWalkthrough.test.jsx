import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../api/client.js", () => ({ apiFetch: vi.fn() }));

import TlsWalkthrough from "./TlsWalkthrough.jsx";
import { apiFetch } from "../api/client.js";

const mockFetch = vi.mocked(apiFetch);

const HANDSHAKE = {
  suite: "TLS_AES_128_GCM_SHA256",
  steps: [
    {
      idx: 0,
      name: "client_hello",
      detail: "Client offers X25519 key share.",
      client_random: "aabbccdd",
    },
    {
      idx: 1,
      name: "server_hello",
      detail: "Server picks the suite.",
      server_random: "11223344",
    },
    {
      idx: 7,
      name: "resumption",
      detail: "PSK ticket issued.",
      ticket_age_add: "deadbeef",
    },
  ],
};

const DOWNGRADE = {
  attack_steps: [
    "Attacker strips supported_versions extension.",
    "Server falls back to TLS 1.2.",
  ],
  defenses: [
    {
      name: "DOWNGRD sentinel",
      detail: "ServerHello.random last 8 bytes signal a fallback.",
      server_random_with_sentinel: "ff…444F574E47524401",
    },
    {
      name: "TLS 1.3 encryption",
      detail: "The middlebox cannot edit what it cannot read.",
    },
  ],
  countermeasure: "Never configure fallback to TLS < 1.2.",
};

beforeEach(() => {
  mockFetch.mockReset();
  mockFetch.mockImplementation((path) =>
    Promise.resolve(
      path.startsWith("/tls13/handshake") ? HANDSHAKE : DOWNGRADE,
    ),
  );
});

describe("TlsWalkthrough", () => {
  it("loads both panels on mount and lists the handshake steps", async () => {
    render(<TlsWalkthrough />);
    await waitFor(() =>
      expect(screen.getByText(/① ClientHello/i)).toBeTruthy(),
    );
    expect(screen.getByText(/⑧ Resumption/i)).toBeTruthy();
    expect(screen.getByText(/DOWNGRD sentinel/i)).toBeTruthy();
    expect(mockFetch).toHaveBeenCalledWith(
      "/tls13/handshake?suite=TLS_AES_128_GCM_SHA256",
    );
    expect(mockFetch).toHaveBeenCalledWith("/tls13/downgrade");
  });

  it("keeps hex dumps hidden until a step is expanded", async () => {
    render(<TlsWalkthrough />);
    await waitFor(() =>
      expect(screen.getByText(/① ClientHello/i)).toBeTruthy(),
    );
    expect(screen.queryByText(/client_random: aabbccdd/i)).toBeNull();

    const toggle = screen.getByRole("button", { name: /① ClientHello/i });
    expect(toggle.getAttribute("aria-expanded")).toBe("false");
    fireEvent.click(toggle);
    expect(toggle.getAttribute("aria-expanded")).toBe("true");
    expect(screen.getByText(/client_random: aabbccdd/i)).toBeTruthy();
    // other steps stay collapsed
    expect(screen.queryByText(/server_random: 11223344/i)).toBeNull();
  });

  it("re-runs the handshake when the button is clicked", async () => {
    render(<TlsWalkthrough />);
    await waitFor(() =>
      expect(screen.getByText(/Re-run handshake/i)).toBeTruthy(),
    );
    fireEvent.click(screen.getByText(/Re-run handshake/i));
    await waitFor(() =>
      expect(
        mockFetch.mock.calls.filter(([p]) => p.startsWith("/tls13/handshake"))
          .length,
      ).toBe(2),
    );
  });

  it("shows the downgrade sentinel bytes in the defence list", async () => {
    render(<TlsWalkthrough />);
    await waitFor(() =>
      expect(screen.getByText(/444F574E47524401/)).toBeTruthy(),
    );
    expect(screen.getByText(/Never configure fallback/i)).toBeTruthy();
  });
});
