import { useState } from 'react';

import { useInteractionCheck } from '../hooks/useInteractionCheck';
import { validateDrugList } from '../lib/validation';
import { DrugSearch } from './DrugSearch';
import { InteractionResult } from './InteractionResult';
import { SeverityBanner } from './SeverityBanner';

/**
 * Main feature component: collect a drug list, submit a check, and render
 * severity-coded results with loading/error/validation states.
 */
export function InteractionChecker() {
  const [drugs, setDrugs] = useState<string[]>([]);
  const [includeMl, setIncludeMl] = useState(true);
  const { mutate, data, isPending, isError, error, reset } = useInteractionCheck();

  const addDrug = (name: string) => setDrugs((prev) => [...prev, name]);
  const removeDrug = (index: number) => setDrugs((prev) => prev.filter((_, i) => i !== index));

  const listCheck = validateDrugList(drugs);

  const runCheck = () => {
    if (!listCheck.valid) return;
    reset();
    mutate({ drugs: drugs.map((name) => ({ name })), includeMl });
  };

  return (
    <section className="interaction-checker">
      <header>
        <h1>Drug Interaction Checker</h1>
        <p className="subtitle">
          Check a medication list for drug–drug interactions. Predictions marked
          “predicted” are advisory and not clinically confirmed.
        </p>
      </header>

      <DrugSearch existing={drugs} onAdd={addDrug} />

      {drugs.length > 0 && (
        <ul className="selected-drugs">
          {drugs.map((d, i) => (
            <li key={`${d}-${i}`} className="chip">
              {d}
              <button type="button" aria-label={`Remove ${d}`} onClick={() => removeDrug(i)}>
                ×
              </button>
            </li>
          ))}
        </ul>
      )}

      <div className="controls">
        <label className="toggle">
          <input
            type="checkbox"
            checked={includeMl}
            onChange={(e) => setIncludeMl(e.target.checked)}
          />
          Include ML severity predictions
        </label>
        <button
          type="button"
          className="primary"
          disabled={!listCheck.valid || isPending}
          onClick={runCheck}
        >
          {isPending ? 'Checking…' : 'Check Interactions'}
        </button>
      </div>

      {!listCheck.valid && drugs.length > 0 && <p className="hint">{listCheck.error}</p>}

      {isError && (
        <p role="alert" className="error">
          Failed to check interactions: {error.message}
        </p>
      )}

      {data && (
        <>
          <SeverityBanner severity={data.highest_severity} />
          <InteractionResult result={data} />
        </>
      )}
    </section>
  );
}
