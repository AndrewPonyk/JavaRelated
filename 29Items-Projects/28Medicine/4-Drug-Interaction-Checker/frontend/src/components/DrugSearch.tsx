import { useState } from 'react';

import { useDrugSearch } from '../hooks/useDrugSearch';
import { canAddDrug, normalizeDrugName } from '../lib/validation';

interface DrugSearchProps {
  existing: string[];
  onAdd: (name: string) => void;
}

/** Drug entry with debounced backend suggestion + inline validation. */
export function DrugSearch({ existing, onAdd }: DrugSearchProps) {
  const [value, setValue] = useState('');
  const [error, setError] = useState<string | null>(null);
  const { data, isFetching } = useDrugSearch(value);

  const add = (name: string) => {
    const check = canAddDrug(existing, name);
    if (!check.ok) {
      setError(check.reason ?? 'Invalid entry.');
      return;
    }
    setError(null);
    onAdd(normalizeDrugName(name));
    setValue('');
  };

  const suggestion = data?.matches?.[0]?.name;
  const showSuggestion =
    !!suggestion &&
    normalizeDrugName(suggestion).toLowerCase() !== normalizeDrugName(value).toLowerCase();

  return (
    <div className="drug-search">
      <div className="drug-search__row">
        <input
          aria-label="Drug name"
          value={value}
          placeholder="Enter a drug name (e.g., warfarin)"
          onChange={(e) => {
            setValue(e.target.value);
            setError(null);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') add(value);
          }}
        />
        <button type="button" onClick={() => add(value)}>
          Add
        </button>
      </div>
      {isFetching && <span className="hint">Searching…</span>}
      {showSuggestion && (
        <button type="button" className="suggestion" onClick={() => add(suggestion)}>
          Use “{suggestion}”
        </button>
      )}
      {error && (
        <span role="alert" className="error">
          {error}
        </span>
      )}
    </div>
  );
}
