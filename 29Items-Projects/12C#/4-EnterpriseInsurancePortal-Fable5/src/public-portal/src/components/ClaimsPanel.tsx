import { useEffect, useState, type FormEvent } from 'react';
import { fileClaim, getClaims, type ClaimSummary, type PolicySummary } from '../api/client';

interface Props {
  policy: PolicySummary;
}

const currency = (value: number) =>
  value.toLocaleString(undefined, { style: 'currency', currency: 'USD' });

export function ClaimsPanel({ policy }: Props) {
  const [claims, setClaims] = useState<ClaimSummary[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [formErrors, setFormErrors] = useState<string[]>([]);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setClaims(null);
    setLoadError(null);
    setNotice(null);
    getClaims(policy.id)
      .then((result) => {
        if (!cancelled) setClaims(result);
      })
      .catch((err: Error) => {
        if (!cancelled) setLoadError(err.message);
      });
    return () => {
      cancelled = true;
    };
  }, [policy.id]);

  const validate = (): string[] => {
    const errors: string[] = [];
    if (!description.trim()) errors.push('Describe what happened.');
    if (description.length > 2000) errors.push('Description is too long (max 2000 characters).');
    const parsed = Number(amount);
    if (!amount || Number.isNaN(parsed) || parsed <= 0)
      errors.push('Enter a positive claim amount.');
    return errors;
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitError(null);
    setNotice(null);

    const errors = validate();
    setFormErrors(errors);
    if (errors.length > 0) return;

    setSubmitting(true);
    try {
      const claim = await fileClaim(policy.id, description.trim(), Number(amount));
      setClaims((existing) => [claim, ...(existing ?? [])]);
      setDescription('');
      setAmount('');
      setNotice('Your claim has been filed. We will be in touch shortly.');
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Filing the claim failed.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="card">
      <h2>Claims — {policy.policyNumber}</h2>

      {policy.status === 'Active' ? (
        <form onSubmit={onSubmit} noValidate>
          <div className="form-row">
            <label htmlFor="claim-description">What happened?</label>
            <input
              id="claim-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. Rear-end collision on I-5"
            />
          </div>
          <div className="form-row">
            <label htmlFor="claim-amount">Estimated amount (USD)</label>
            <input
              id="claim-amount"
              type="number"
              min="0.01"
              step="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          </div>
          {formErrors.length > 0 && (
            <ul className="error" role="alert">
              {formErrors.map((err) => (
                <li key={err}>{err}</li>
              ))}
            </ul>
          )}
          {submitError && (
            <p className="error" role="alert">
              {submitError}
            </p>
          )}
          {notice && <p className="success">{notice}</p>}
          <button type="submit" disabled={submitting}>
            {submitting ? 'Filing…' : 'File claim'}
          </button>
        </form>
      ) : (
        <p className="muted">Claims can only be filed against active policies.</p>
      )}

      {loadError && (
        <p className="error" role="alert">
          {loadError}
        </p>
      )}
      {claims === null && !loadError && <p className="muted">Loading claims…</p>}
      {claims !== null && claims.length === 0 && <p className="muted">No claims on this policy.</p>}
      {claims !== null && claims.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Filed</th>
              <th>Description</th>
              <th>Claimed</th>
              <th>Approved</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {claims.map((c) => (
              <tr key={c.id}>
                <td>{new Date(c.filedAtUtc).toLocaleDateString()}</td>
                <td>{c.description}</td>
                <td>{currency(c.claimedAmount)}</td>
                <td>{c.approvedAmount !== null ? currency(c.approvedAmount) : '—'}</td>
                <td>
                  <span className={`badge badge-${c.status.toLowerCase()}`}>{c.status}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
