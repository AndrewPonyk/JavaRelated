/**
 * KaTeX rendering of backend-produced LaTeX.
 *
 * Security: `trust: false` (no \href/\includegraphics) and KaTeX escapes its
 * own HTML output, which is why dangerouslySetInnerHTML is acceptable here —
 * and ONLY here. Never render user-typed LaTeX with trust enabled.
 */

import katex from "katex";
import "katex/dist/katex.min.css";
import { useMemo } from "react";

interface LatexBlockProps {
  latex: string;
  displayMode?: boolean;
}

export function LatexBlock({ latex, displayMode = false }: LatexBlockProps) {
  const html = useMemo(
    () =>
      katex.renderToString(latex, {
        displayMode,
        throwOnError: false, // render errors in red instead of crashing the tree
        trust: false,
        output: "html",
      }),
    [latex, displayMode],
  );

  return <span className="latex-block" dangerouslySetInnerHTML={{ __html: html }} />;
}
