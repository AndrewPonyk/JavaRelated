import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../auth/AuthContext";
import { AuthPanel } from "./AuthPanel";

function renderPanel() {
  return render(
    <AuthProvider>
      <AuthPanel />
    </AuthProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe("AuthPanel", () => {
  it("validates email format before hitting the server", async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    renderPanel();

    await userEvent.type(screen.getByLabelText(/email/i), "not-an-email");
    await userEvent.type(screen.getByLabelText(/password/i), "long-enough-8");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/valid email/i);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("enforces the 8-character password policy client-side", async () => {
    vi.stubGlobal("fetch", vi.fn());
    renderPanel();

    await userEvent.type(screen.getByLabelText(/email/i), "ada@example.edu");
    await userEvent.type(screen.getByLabelText(/password/i), "short");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/at least 8 characters/i);
  });

  it("surfaces server problem details (e.g. bad credentials)", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(JSON.stringify({ detail: "Incorrect email or password." }), {
            status: 401,
            headers: { "Content-Type": "application/json" },
          }),
        ),
    );
    renderPanel();

    await userEvent.type(screen.getByLabelText(/email/i), "ada@example.edu");
    await userEvent.type(screen.getByLabelText(/password/i), "wrong-password-1");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/incorrect email or password/i);
  });

  it("switches between sign-in and register modes", async () => {
    vi.stubGlobal("fetch", vi.fn());
    renderPanel();

    await userEvent.click(screen.getByRole("button", { name: /need an account/i }));
    expect(screen.getByRole("heading", { name: /create account/i })).toBeInTheDocument();
  });
});
