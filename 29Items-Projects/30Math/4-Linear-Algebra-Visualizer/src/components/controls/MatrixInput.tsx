import { useEffect, useState } from 'react';

import { IDENTITY, mat2, rotation, scaling, shear, type Mat2 } from '@/core/math/matrix2';
import { useVisualizerStore } from '@/state/visualizerStore';

type EntryKey = 'a' | 'b' | 'c' | 'd';
type Draft = Record<EntryKey, string>;

const ENTRY_KEYS: EntryKey[] = ['a', 'b', 'c', 'd'];

const PRESETS: { label: string; matrix: Mat2 }[] = [
  { label: 'Identity', matrix: IDENTITY },
  { label: 'Rotate 90°', matrix: rotation(Math.PI / 2) },
  { label: 'Shear', matrix: shear(1) },
  { label: 'Scale ×2', matrix: scaling(2) },
  { label: 'Reflect X', matrix: mat2(1, 0, 0, -1) },
];

const formatEntry = (n: number): string => String(Number(n.toFixed(4)));

const toDraft = (m: Mat2): Draft => ({
  a: formatEntry(m.a),
  b: formatEntry(m.b),
  c: formatEntry(m.c),
  d: formatEntry(m.d),
});

/**
 * 2×2 matrix editor. Local draft state; the store only ever receives
 * validated finite numbers (invalid input is unrepresentable downstream —
 * ARCHITECTURE.md §2.6). The draft resyncs whenever the store matrix changes
 * from elsewhere (presets, exercises).
 */
export function MatrixInput() {
  const setMatrix = useVisualizerStore((s) => s.setMatrix);
  const targetMatrix = useVisualizerStore((s) => s.targetMatrix);
  const [draft, setDraft] = useState<Draft>(() => toDraft(IDENTITY));
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setDraft(toDraft(targetMatrix));
    setError(null);
  }, [targetMatrix]);

  const applyDraft = () => {
    const parsed = ENTRY_KEYS.map((key) => Number.parseFloat(draft[key]));
    if (parsed.some((n) => !Number.isFinite(n))) {
      setError('All four entries must be numbers.');
      return;
    }
    setError(null);
    setMatrix(mat2(parsed[0], parsed[1], parsed[2], parsed[3]));
  };

  return (
    <div className="matrix-input">
      <div className="matrix-grid" role="group" aria-label="Matrix entries">
        {ENTRY_KEYS.map((key) => (
          <input
            key={key}
            aria-label={`Matrix entry ${key}`}
            inputMode="decimal"
            value={draft[key]}
            onChange={(e) => setDraft((d) => ({ ...d, [key]: e.target.value }))}
            onKeyDown={(e) => {
              if (e.key === 'Enter') applyDraft();
            }}
          />
        ))}
      </div>

      {error && (
        <p className="input-error" role="alert">
          {error}
        </p>
      )}

      <div className="button-row">
        <button type="button" onClick={applyDraft}>
          Apply
        </button>
        {PRESETS.map((preset) => (
          <button key={preset.label} type="button" onClick={() => setMatrix(preset.matrix)}>
            {preset.label}
          </button>
        ))}
      </div>
    </div>
  );
}
