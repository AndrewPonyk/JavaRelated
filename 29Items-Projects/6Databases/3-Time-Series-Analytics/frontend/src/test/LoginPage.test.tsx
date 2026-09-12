import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { api, ApiError } from "../api/client";
import { LoginPage } from "../pages/LoginPage";

vi.mock("../api/client", async (importOriginal) => {
  const original = await importOriginal<typeof import("../api/client")>();
  return { ...original, api: { ...original.api, login: vi.fn() } };
});

describe("LoginPage", () => {
  beforeEach(() => vi.clearAllMocks());

  const fill = (username: string, password: string) => {
    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: username } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: password } });
  };

  it("disables submit until both fields are filled", () => {
    render(<LoginPage onLogin={vi.fn()} />);
    const button = screen.getByRole("button", { name: /sign in/i }) as HTMLButtonElement;
    expect(button.disabled).toBe(true);

    fill("andrii", "s3cret");
    expect(button.disabled).toBe(false);
  });

  it("calls onLogin with token and roles on success", async () => {
    (api.login as Mock).mockResolvedValue({
      access_token: "tok",
      token_type: "bearer",
      expires_in: 1800,
      roles: ["admin"],
    });
    const onLogin = vi.fn();
    render(<LoginPage onLogin={onLogin} />);

    fill("andrii", "s3cret");
    fireEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() => expect(onLogin).toHaveBeenCalledWith("tok", ["admin"]));
    expect(api.login).toHaveBeenCalledWith("andrii", "s3cret");
  });

  it("shows the backend error message on failure", async () => {
    (api.login as Mock).mockRejectedValue(new ApiError(401, "Invalid username or password"));
    render(<LoginPage onLogin={vi.fn()} />);

    fill("andrii", "wrong");
    fireEvent.click(screen.getByRole("button", { name: /sign in/i }));

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toContain("Invalid username or password");
  });
});
