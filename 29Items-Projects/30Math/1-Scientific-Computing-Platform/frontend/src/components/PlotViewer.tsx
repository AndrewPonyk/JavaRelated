/**
 * Fetches an SVG plot from the backend and displays it via an object URL.
 * Same loading/error discipline as EquationSolver; revokes URLs on cleanup.
 */

import { useCallback, useEffect, useState } from "react";
import { renderFunctionPlot } from "../api/symbolic";

interface PlotViewerProps {
  expression: string;
  variable?: string;
  xMin?: number;
  xMax?: number;
}

type PlotState =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "success"; objectUrl: string }
  | { status: "error"; message: string };

export function PlotViewer({ expression, variable = "x", xMin = -10, xMax = 10 }: PlotViewerProps) {
  const [state, setState] = useState<PlotState>({ status: "idle" });

  const renderPlot = useCallback(async () => {
    setState({ status: "loading" });
    try {
      const blob = await renderFunctionPlot(expression, variable, xMin, xMax);
      setState({ status: "success", objectUrl: URL.createObjectURL(blob) });
    } catch {
      setState({ status: "error", message: "Plot rendering failed for this expression." });
    }
  }, [expression, variable, xMin, xMax]);

  useEffect(() => {
    return () => {
      if (state.status === "success") URL.revokeObjectURL(state.objectUrl);
    };
  }, [state]);

  return (
    <section className="card" aria-label="Function plot">
      <h2>Plot</h2>
      <p className="muted">
        f({variable}) = <code>{expression}</code> on [{xMin}, {xMax}]
      </p>
      <button onClick={() => void renderPlot()} disabled={state.status === "loading"}>
        {state.status === "loading" ? "Rendering…" : "Render plot"}
      </button>

      {state.status === "error" && (
        <p role="alert" className="error-banner">
          {state.message}
        </p>
      )}
      {state.status === "success" && (
        <img src={state.objectUrl} alt={`Plot of ${expression}`} className="plot-image" />
      )}
    </section>
  );
}
