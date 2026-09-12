import { useMemo } from 'react';

import { ExercisePanel } from '@/components/ExercisePanel/ExercisePanel';
import { MathNotation } from '@/components/MathNotation/MathNotation';
import { VectorCanvas } from '@/components/VectorCanvas/VectorCanvas';
import { AnimationControls } from '@/components/controls/AnimationControls';
import { MatrixInput } from '@/components/controls/MatrixInput';
import { VectorControls } from '@/components/controls/VectorControls';
import { determinantToTex, eigenToTex, matrixToTex } from '@/core/format/tex';
import { eigen } from '@/core/math/eigen';
import { useVisualizerStore } from '@/state/visualizerStore';

export default function App() {
  const matrix = useVisualizerStore((s) => s.targetMatrix);
  const eigenTex = useMemo(() => eigenToTex(eigen(matrix)), [matrix]);
  const detTex = useMemo(() => determinantToTex(matrix), [matrix]);

  return (
    <div className="app-shell">
      <header className="app-header">
        <h1>Linear Algebra Visualizer</h1>
        <p className="tagline">See what matrices do — vectors, transformations, eigenvalues.</p>
      </header>

      <main className="app-main">
        <section className="canvas-pane" aria-label="Interactive visualization">
          <VectorCanvas />
        </section>

        <aside className="side-pane">
          <section className="panel">
            <h2>Transformation</h2>
            <MatrixInput />
            <VectorControls />
            <AnimationControls />
          </section>

          <section className="panel" aria-label="Eigen analysis">
            <h2>Eigen analysis</h2>
            <p>
              <MathNotation tex={`A = ${matrixToTex(matrix)}`} />
            </p>
            <p>
              <MathNotation tex={detTex} />
            </p>
            <p>
              <MathNotation tex={eigenTex} />
            </p>
          </section>

          <ExercisePanel />
        </aside>
      </main>
    </div>
  );
}
