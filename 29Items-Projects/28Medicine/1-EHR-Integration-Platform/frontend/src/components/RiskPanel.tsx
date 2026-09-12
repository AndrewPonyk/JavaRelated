/**
 * RiskPanel — submit a clinical note and get a risk tier from similar notes
 * (entity-linking service). Demonstrates mutation + loading/error/result states.
 */
import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { stratify } from '@/api/mlApi';

interface RiskPanelProps {
  patientId?: string;
}

export function RiskPanel({ patientId = '' }: RiskPanelProps): JSX.Element {
  const [note, setNote] = useState('');
  const [topK, setTopK] = useState(5);

  const mutation = useMutation({ mutationFn: stratify });

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!note.trim()) return;
    mutation.mutate({ patientId: patientId || 'unknown', noteText: note, topK });
  }

  const result = mutation.data;

  return (
    <section className="card">
      <h2>Risk stratification</h2>
      <p className="muted">Find similar clinical notes and estimate a risk tier.</p>
      <form onSubmit={handleSubmit} className="form">
        <label className="field">
          <span>Clinical note</span>
          <textarea
            rows={4}
            value={note}
            placeholder="e.g. chest pain, shortness of breath, elevated troponin"
            onChange={(e) => setNote(e.target.value)}
          />
        </label>
        <label className="field">
          <span>Similar notes (k)</span>
          <input
            type="number"
            min={1}
            max={50}
            value={topK}
            onChange={(e) => setTopK(Number(e.target.value))}
          />
        </label>
        <button type="submit" disabled={mutation.isPending || !note.trim()}>
          {mutation.isPending ? 'Scoring…' : 'Stratify'}
        </button>
      </form>

      {mutation.isError && (
        <p role="alert" className="error">
          {(mutation.error as Error).message}
        </p>
      )}

      {result && (
        <div className="risk-result" role="status">
          <span className={`badge badge--${result.riskTier.toLowerCase()}`}>{result.riskTier}</span>
          <span className="risk-score">score {result.riskScore.toFixed(3)}</span>
          {result.cohort.length > 0 && (
            <p className="muted">Similar notes: {result.cohort.join(', ')}</p>
          )}
        </div>
      )}
    </section>
  );
}
