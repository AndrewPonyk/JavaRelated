/**
 * Reference component for the platform's data-fetching + display pattern:
 * controlled form → cancellable request via hook → explicit loading / error /
 * queued / success states → LaTeX-rendered results with derivation steps.
 */

import { useState, type FormEvent } from "react";
import { useEquationSolver } from "../hooks/useEquationSolver";
import { LatexBlock } from "./LatexBlock";

export function EquationSolver() {
  const [expression, setExpression] = useState("x^2 - 4 = 0");
  const [variable, setVariable] = useState("x");
  const [showSteps, setShowSteps] = useState(false);
  const { state, solve } = useEquationSolver();

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (expression.trim().length === 0) return;
    void solve(expression, variable);
  };

  return (
    <section className="card" aria-label="Equation solver">
      <h2>Solve an equation</h2>

      <form onSubmit={handleSubmit} className="solver-form">
        <label htmlFor="expression">Equation</label>
        <input
          id="expression"
          value={expression}
          onChange={(e) => setExpression(e.target.value)}
          placeholder="e.g. x^2 - 4 = 0"
          maxLength={512}
          autoComplete="off"
          required
        />

        <label htmlFor="variable">Solve for</label>
        <input
          id="variable"
          value={variable}
          onChange={(e) => setVariable(e.target.value)}
          maxLength={16}
          pattern="[a-zA-Z][a-zA-Z0-9_]*"
          title="A variable name like x or t_1"
          className="variable-input"
          required
        />

        <button type="submit" disabled={state.status === "loading"}>
          {state.status === "loading" ? "Solving…" : "Solve"}
        </button>
      </form>

      {state.status === "loading" && (
        <p role="status" className="muted">
          Computing symbolic solution…
        </p>
      )}

      {state.status === "error" && (
        <p role="alert" className="error-banner">
          {state.message}
        </p>
      )}

      {state.status === "queued" && (
        <p role="status" className="info-banner">
          This one is heavy — it is running as a background job. Track it under{" "}
          <strong>My computations</strong> (id {state.queued.computation_id.slice(0, 8)}…).
        </p>
      )}

      {state.status === "success" && (
        <div className="results">
          <p>
            Equation: <LatexBlock latex={state.result.equation_latex} />
          </p>
          {state.result.solutions_latex.length === 0 ? (
            <p className="muted">No solutions found.</p>
          ) : (
            <ul aria-label="Solutions">
              {state.result.solutions_latex.map((latex, i) => (
                <li key={state.result.solutions[i]}>
                  <LatexBlock latex={`${state.result.variable} = ${latex}`} displayMode />
                </li>
              ))}
            </ul>
          )}
          {state.result.steps_latex.length > 0 && (
            <div>
              <button type="button" className="link-button" onClick={() => setShowSteps((v) => !v)}>
                {showSteps ? "Hide steps" : "Show steps"}
              </button>
              {showSteps && (
                <div className="steps" aria-label="Derivation steps">
                  <LatexBlock latex={state.result.derivation_latex} displayMode />
                </div>
              )}
            </div>
          )}
          {state.result.cached && <p className="muted">Served from cache.</p>}
        </div>
      )}
    </section>
  );
}
