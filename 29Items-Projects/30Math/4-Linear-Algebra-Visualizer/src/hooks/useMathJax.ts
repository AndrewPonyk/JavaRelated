import { useEffect, type RefObject } from 'react';

/**
 * Typesets `ref`'s subtree with MathJax whenever `deps` change.
 *
 * Design constraints (TECH-NOTES §3.6 pitfall 4):
 *  - only the changed subtree is typeset (never the whole document);
 *  - no-ops gracefully when MathJax hasn't loaded (jsdom, CDN blocked) —
 *    the raw TeX string remains visible as a degraded fallback;
 *  - clears MathJax's internal state for the element on cleanup.
 */
export function useMathJaxTypeset(
  ref: RefObject<HTMLElement | null>,
  deps: readonly unknown[],
): void {
  useEffect(() => {
    const element = ref.current;
    const mathJax = window.MathJax;
    if (!element || typeof mathJax?.typesetPromise !== 'function') return;

    let cancelled = false;
    void mathJax.typesetPromise([element]).catch((error: unknown) => {
      if (!cancelled) console.warn('MathJax typeset failed', error);
    });

    return () => {
      cancelled = true;
      mathJax.typesetClear?.([element]);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- caller-controlled dependency list
  }, deps as unknown[]);
}
