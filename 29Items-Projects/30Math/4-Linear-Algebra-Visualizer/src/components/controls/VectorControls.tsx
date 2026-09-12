import { formatNumber } from '@/core/format/tex';
import { MAX_USER_VECTORS, useVisualizerStore } from '@/state/visualizerStore';

/**
 * User-defined vectors: add/remove here, drag their tips directly on the
 * canvas. Coordinates shown are in INPUT space; the scene draws A·v.
 */
export function VectorControls() {
  const userVectors = useVisualizerStore((s) => s.userVectors);
  const addVector = useVisualizerStore((s) => s.addVector);
  const removeVector = useVisualizerStore((s) => s.removeVector);

  return (
    <div className="vector-controls">
      <div className="button-row">
        <button type="button" onClick={addVector} disabled={userVectors.length >= MAX_USER_VECTORS}>
          Add vector
        </button>
      </div>

      {userVectors.length > 0 && (
        <ul className="vector-list" aria-label="User vectors">
          {userVectors.map((entry) => (
            <li key={entry.id} className="vector-row">
              <span className="vector-swatch" style={{ backgroundColor: entry.color }} />
              <span className="vector-coords">
                {entry.label} = ({formatNumber(entry.v.x, 2)}, {formatNumber(entry.v.y, 2)})
              </span>
              <button
                type="button"
                onClick={() => removeVector(entry.id)}
                aria-label={`Remove ${entry.label}`}
              >
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}
      {userVectors.length > 0 && (
        <p className="vector-hint">Drag a vector tip on the canvas to move it.</p>
      )}
    </div>
  );
}
