import type { InteractionCheckResponse } from '../types';
import { SEVERITY_COLOR, SEVERITY_LABEL } from '../lib/severity';

interface Props {
  result: InteractionCheckResponse;
}

/** Renders the interaction results, color-coded by severity. */
export function InteractionResult({ result }: Props) {
  return (
    <div className="interaction-result">
      {result.unresolved.length > 0 && (
        <p className="warning">Could not resolve: {result.unresolved.join(', ')}</p>
      )}

      {result.interactions.length === 0 ? (
        <p className="empty">No known interactions found for the selected drugs.</p>
      ) : (
        <ul className="interaction-list">
          {result.interactions.map((i) => (
            <li key={`${i.rxcui_a}-${i.rxcui_b}`} className="interaction-item">
              <span className="severity-badge" style={{ backgroundColor: SEVERITY_COLOR[i.severity] }}>
                {SEVERITY_LABEL[i.severity]}
                {i.ml_predicted ? ' · predicted' : ''}
              </span>
              <div className="interaction-body">
                <strong>
                  {i.name_a} + {i.name_b}
                </strong>
                {i.description && <p className="description">{i.description}</p>}
                {i.mechanism && <p className="mechanism">Mechanism: {i.mechanism}</p>}
                {i.ml_predicted && i.ml_confidence != null && (
                  <small className="confidence">
                    Model confidence: {(i.ml_confidence * 100).toFixed(0)}%
                  </small>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
