import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../api/client.js", () => ({
  apiFetch: vi.fn(),
  auth: {
    _token: null,
    getToken() {
      return this._token;
    },
    setToken(t) {
      this._token = t;
    },
    authHeaders() {
      return this._token ? { Authorization: `Bearer ${this._token}` } : {};
    },
  },
}));

import Login from "./Login.jsx";
import { apiFetch, auth } from "../api/client.js";

const mockFetch = vi.mocked(apiFetch);

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={["/login"]}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route
          path="*"
          element={<div data-testid="moved-on">left the login page</div>}
        />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  mockFetch.mockReset();
  auth.setToken(null);
});

describe("Login", () => {
  it("validates before hitting the API (register)", () => {
    renderLogin();
    fireEvent.click(screen.getByRole("button", { name: "Register" }));
    fireEvent.change(screen.getByTestId("login-email"), {
      target: { value: "not-an-email" },
    });
    fireEvent.change(screen.getByTestId("login-password"), {
      target: { value: "short" },
    });
    fireEvent.click(screen.getByTestId("login-submit"));

    expect(screen.getByText(/valid email/i)).toBeTruthy();
    expect(screen.getByText(/at least 8 characters/i)).toBeTruthy();
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("registers, stores the token, offers TOTP enrolment, and completes it", async () => {
    mockFetch
      .mockResolvedValueOnce({
        token: "tok-1",
        user: { email: "admin@demo.io", is_admin: true },
      })
      .mockResolvedValueOnce({
        secret_base32: "JBSWY3DPEHPK3PXP",
        otpauth_uri: "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP",
      })
      .mockResolvedValueOnce({ totp_enabled: true });

    renderLogin();
    fireEvent.click(screen.getByRole("button", { name: "Register" }));
    fireEvent.change(screen.getByTestId("login-email"), {
      target: { value: "admin@demo.io" },
    });
    fireEvent.change(screen.getByTestId("login-password"), {
      target: { value: "longenough1" },
    });
    fireEvent.click(screen.getByTestId("login-submit"));

    // first user becomes admin — the enrolment panel says so
    await screen.findByText(/you are the admin/i);
    expect(auth.getToken()).toBe("tok-1");

    fireEvent.click(screen.getByTestId("totp-start"));
    expect(await screen.findByTestId("totp-secret")).toBeTruthy();
    expect(screen.getByText(/otpauth:\/\//)).toBeTruthy();

    fireEvent.change(screen.getByTestId("totp-code"), {
      target: { value: "123456" },
    });
    fireEvent.click(screen.getByTestId("totp-submit"));

    await waitFor(() =>
      expect(mockFetch).toHaveBeenCalledWith("/auth/totp/enable", {
        method: "POST",
        auth: true,
        body: { code: "123456", secret_base32: "JBSWY3DPEHPK3PXP" },
      }),
    );
    await screen.findByTestId("moved-on"); // navigated away after enabling
  });

  it("asks for a TOTP code when the backend demands one at login", async () => {
    mockFetch
      .mockRejectedValueOnce(new Error("TOTP code required."))
      .mockResolvedValueOnce({
        token: "tok-2",
        user: { email: "u@demo.io", is_admin: false },
      });

    renderLogin();
    fireEvent.change(screen.getByTestId("login-email"), {
      target: { value: "u@demo.io" },
    });
    fireEvent.change(screen.getByTestId("login-password"), {
      target: { value: "longenough1" },
    });
    fireEvent.click(screen.getByTestId("login-submit"));

    await screen.findByText(/two-factor code required/i);
    fireEvent.change(screen.getByTestId("totp-code"), {
      target: { value: "654321" },
    });
    fireEvent.click(screen.getByTestId("totp-submit"));

    await waitFor(() =>
      expect(mockFetch).toHaveBeenLastCalledWith("/auth/login", {
        method: "POST",
        body: {
          email: "u@demo.io",
          password: "longenough1",
          totp_code: "654321",
        },
      }),
    );
    await screen.findByTestId("moved-on");
    expect(auth.getToken()).toBe("tok-2");
  });

  it("shows the server error for bad credentials", async () => {
    mockFetch.mockRejectedValueOnce(new Error("Invalid email or password."));

    renderLogin();
    fireEvent.change(screen.getByTestId("login-email"), {
      target: { value: "u@demo.io" },
    });
    fireEvent.change(screen.getByTestId("login-password"), {
      target: { value: "wrongpass1" },
    });
    fireEvent.click(screen.getByTestId("login-submit"));

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toMatch(/invalid email or password/i);
  });
});
