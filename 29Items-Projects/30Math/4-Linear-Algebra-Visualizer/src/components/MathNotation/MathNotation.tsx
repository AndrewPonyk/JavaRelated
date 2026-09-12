import { useRef } from 'react';

import { useMathJaxTypeset } from '@/hooks/useMathJax';

interface MathNotationProps {
  /** LaTeX source WITHOUT delimiters — this component adds \( \) or \[ \]. */
  tex: string;
  /** Display-style (block) math instead of inline. */
  block?: boolean;
  className?: string;
}

/**
 * Renders LaTeX via MathJax. Degrades to the raw TeX string when MathJax
 * is unavailable (jsdom, blocked CDN) — never crashes the tree.
 */
export function MathNotation({ tex, block = false, className }: MathNotationProps) {
  const ref = useRef<HTMLSpanElement>(null);
  useMathJaxTypeset(ref, [tex, block]);

  const wrapped = block ? `\\[${tex}\\]` : `\\(${tex}\\)`;
  return (
    <span ref={ref} className={className}>
      {wrapped}
    </span>
  );
}
