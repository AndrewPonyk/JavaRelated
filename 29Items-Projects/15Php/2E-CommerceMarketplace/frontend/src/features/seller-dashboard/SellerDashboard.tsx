import { useEffect, useState } from 'react';
import { apiGet, ApiError } from '@/api/client';
import { formatPrice } from '@/types/product';

interface CommissionSummary {
  currency: string;
  grossMinor: number;
  commissionMinor: number;
  entries: number;
}

/**
 * Seller dashboard (stub). Shows the authenticated seller's commission summary,
 * accrued asynchronously from order events on the backend.
 *
 * TODO: charts (sales over time), payout history, product management table.
 */
export function SellerDashboard(): JSX.Element {
  const [summary, setSummary] = useState<CommissionSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiGet<CommissionSummary>('/seller/dashboard/commission')
      .then(setSummary)
      .catch((e: unknown) => setError(e instanceof ApiError ? e.message : 'Failed to load'));
  }, []);

  if (error) return <p role="alert">{error}</p>;
  if (!summary) return <p role="status">Loading dashboard…</p>;

  return (
    <section>
      <h2>Commission summary</h2>
      <dl>
        <dt>Gross sales</dt>
        <dd>{formatPrice(summary.grossMinor, summary.currency)}</dd>
        <dt>Commission owed</dt>
        <dd>{formatPrice(summary.commissionMinor, summary.currency)}</dd>
        <dt>Orders</dt>
        <dd>{summary.entries}</dd>
      </dl>
    </section>
  );
}
