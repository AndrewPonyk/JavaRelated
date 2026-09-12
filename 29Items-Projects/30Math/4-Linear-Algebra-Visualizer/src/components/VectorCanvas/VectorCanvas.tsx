import { useEffect, useRef, useState } from 'react';

import { SceneManager } from '@/rendering/SceneManager';

function isWebGLAvailable(): boolean {
  try {
    const canvas = document.createElement('canvas');
    return Boolean(
      window.WebGLRenderingContext && (canvas.getContext('webgl2') ?? canvas.getContext('webgl')),
    );
  } catch {
    return false;
  }
}

/**
 * The React ⇄ Three.js seam: mounts SceneManager exactly once and tears it
 * down on unmount. All further communication goes through the Zustand store —
 * this component never re-renders during animation.
 */
export function VectorCanvas() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    if (!isWebGLAvailable()) {
      setError(
        'WebGL is not available in this browser. The visualizer needs hardware-accelerated graphics — try updating your browser or enabling GPU acceleration.',
      );
      return;
    }

    const manager = new SceneManager(canvas);
    manager.start();
    // StrictMode double-mounts in dev — exhaustive dispose keeps that leak-free.
    return () => manager.dispose();
  }, []);

  if (error) {
    return (
      <div className="canvas-fallback" role="alert">
        <h2>Cannot start the visualizer</h2>
        <p>{error}</p>
      </div>
    );
  }

  return <canvas ref={canvasRef} className="vector-canvas" aria-label="2D vector space" />;
}
