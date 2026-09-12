import { useEffect, useState } from 'react';
import { getMyPolicies, type PolicySummary } from '../api/client';

type LoadState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'loaded'; policies: PolicySummary[] };

interface Props {
  selectedPolicyId: string | null;
  onSelect: (policy: PolicySummary) => void;
  refreshToken?: number;
}

const currency = (value: number) =>
  value.toLocaleString(undefined, { style: 'currency', currency: 'USD' });

export function PolicyList({ selectedPolicyId, onSelect, refreshToken = 0 }: Props) {
  const [state, setState] = useState<LoadState>({ status: 'loading' });

  useEffect(() => {
    let cancelled = false;
    setState({ status: 'loading' });
    getMyPolicies()
      .then((policies) => {
        if (!cancelled) setState({ status: 'loaded', policies });
      })
      .catch((err: Error) => {
        if (!cancelled)
          setState({
            status: 'error',
            message: err.message || 'Could not load your policies. Please try again.',
          });
      });
    return () => {
      cancelled = true;
    };
  }, [refreshToken]);

  if (state.status === 'loading') return <p className="muted">Loading your policies…</p>;
  if (state.status === 'error')
    return (
      <p role="alert" className="error">
        {state.message}
      </p>
    );
  if (state.policies.length === 0)
    return <p className="muted">You have no policies yet. Contact your broker to get covered.</p>;

  return (
    <table>
      <thead>
        <tr>
          <th>Policy #</th>
          <th>Annual premium</th>
          <th>Effective</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        {state.policies.map((p) => (
          <tr
            key={p.id}
            className={p.id === selectedPolicyId ? 'selected' : ''}
            onClick={() => onSelect(p)}
          >
            <td>{p.policyNumber}</td>
            <td>{currency(p.annualPremium)}</td>
            <td>{p.effectiveDate}</td>
            <td>
              <span className={`badge badge-${p.status.toLowerCase()}`}>{p.status}</span>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
