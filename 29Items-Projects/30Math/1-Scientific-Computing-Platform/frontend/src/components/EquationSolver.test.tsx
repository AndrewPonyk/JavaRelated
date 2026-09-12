import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { SolveResponse } from "../types/api";
import { EquationSolver } from "./EquationSolver";

const solveResponse: SolveResponse = {
  equation_latex: "x^{2} - 4 = 0",
  variable: "x",
  solutions: ["-2", "2"],
  solutions_latex: ["-2", "2"],
  steps_latex: ["x^{2} - 4 = 0", "(x-2)(x+2) = 0", "x = -2, x = 2"],
  derivation_latex: "\\begin{aligned}\n& x^{2} - 4 = 0\n\\end{aligned}",
  cached: false,
};

function mockFetchOnce(status: number, body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(JSON.stringify(body), {
        status,
        headers: { "Content-Type": "application/json" },
      }),
    ),
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe("EquationSolver", () => {
  it("renders solutions and expandable steps after a successful solve", async () => {
    mockFetchOnce(200, solveResponse);
    render(<EquationSolver />);

    await userEvent.click(screen.getByRole("button", { name: /solve/i }));

    await waitFor(() => {
      expect(screen.getByRole("list", { name: /solutions/i })).toBeInTheDocument();
    });
    expect(screen.getAllByRole("listitem")).toHaveLength(2);

    await userEvent.click(screen.getByRole("button", { name: /show steps/i }));
    expect(screen.getByLabelText(/derivation steps/i)).toBeInTheDocument();
  });

  it("shows the problem+json detail on API errors", async () => {
    mockFetchOnce(422, {
      type: "https://scp.example.com/problems/expression_parse_error",
      title: "expression parse error",
      status: 422,
      detail: "Expression contains unsupported characters.",
      request_id: "test",
    });
    render(<EquationSolver />);

    await userEvent.click(screen.getByRole("button", { name: /solve/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Expression contains unsupported characters.",
    );
  });

  it("shows the background-job banner on a 202 escalation", async () => {
    mockFetchOnce(202, {
      computation_id: "abcdef1234567890",
      status: "queued",
      detail: "Solve exceeded the interactive budget.",
    });
    render(<EquationSolver />);

    await userEvent.click(screen.getByRole("button", { name: /solve/i }));

    expect(await screen.findByRole("status")).toHaveTextContent(/background job/i);
  });
});
