import { useVisualizerStore, type LayerToggle } from '@/state/visualizerStore';

const TOGGLES: { key: LayerToggle; label: string }[] = [
  { key: 'showGrid', label: 'Transformed grid' },
  { key: 'showUnitSquare', label: 'Unit square (det)' },
  { key: 'showEigenvectors', label: 'Eigenvectors' },
];

/** Playback + layer controls for the WebGL scene. */
export function AnimationControls() {
  const animationSpeed = useVisualizerStore((s) => s.animationSpeed);
  const setAnimationSpeed = useVisualizerStore((s) => s.setAnimationSpeed);
  const replay = useVisualizerStore((s) => s.replay);
  const resetTransform = useVisualizerStore((s) => s.resetTransform);
  // Individual selectors — an object-returning selector would create a fresh
  // snapshot every render and loop under zustand v5's strict equality.
  const showGrid = useVisualizerStore((s) => s.showGrid);
  const showUnitSquare = useVisualizerStore((s) => s.showUnitSquare);
  const showEigenvectors = useVisualizerStore((s) => s.showEigenvectors);
  const toggle = useVisualizerStore((s) => s.toggle);
  const toggles = { showGrid, showUnitSquare, showEigenvectors };

  return (
    <div className="animation-controls">
      <div className="button-row">
        <button type="button" onClick={replay}>
          Replay
        </button>
        <button type="button" onClick={resetTransform}>
          Reset to identity
        </button>
      </div>

      <label className="speed-control">
        Speed ×{animationSpeed.toFixed(2)}
        <input
          type="range"
          min={0.25}
          max={3}
          step={0.25}
          value={animationSpeed}
          onChange={(e) => setAnimationSpeed(Number(e.target.value))}
        />
      </label>

      <fieldset className="layer-toggles">
        <legend>Layers</legend>
        {TOGGLES.map(({ key, label }) => (
          <label key={key}>
            <input type="checkbox" checked={toggles[key]} onChange={() => toggle(key)} />
            {label}
          </label>
        ))}
      </fieldset>
    </div>
  );
}
