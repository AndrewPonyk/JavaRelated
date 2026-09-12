import { activityListSchema, type ActivityEntry } from '../dashboard.api';
import { LoadingSpinner } from '@/components/LoadingSpinner';
import { useFetch } from '@/hooks/useFetch';

function formatAmount(entry: ActivityEntry): string {
  const formatted = new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: entry.currency,
    signDisplay: 'always',
  }).format(entry.amount);
  return formatted;
}

/** The dashboard shows a digest, not a ledger — the API paginates via ?limit=. */
const FEED_LIMIT = 10;

export function ActivityFeed() {
  const { data, error, status, refetch } = useFetch<ActivityEntry[]>(
    `/v1/activity?limit=${FEED_LIMIT}`,
    { schema: activityListSchema },
  );

  if (status === 'idle' || status === 'loading') {
    return <LoadingSpinner label="Loading recent activity…" />;
  }

  if (status === 'error') {
    return (
      <div role="alert" className="error-panel">
        <p>We could not load your recent activity{error ? ` (${error.code})` : ''}.</p>
        <button type="button" onClick={refetch}>
          Retry
        </button>
      </div>
    );
  }

  if (!data || data.length === 0) {
    return <p className="empty-state">No account activity yet.</p>;
  }

  return (
    <section aria-labelledby="activity-heading" className="activity">
      <h2 id="activity-heading">Recent activity</h2>
      <ul>
        {data.map((entry) => (
          <li key={entry.id} className="activity__row">
            <div>
              <p className="activity__description">{entry.description}</p>
              <time dateTime={entry.date} className="activity__date">
                {new Date(entry.date).toLocaleDateString()}
              </time>
            </div>
            <span
              className={`activity__amount ${entry.amount >= 0 ? 'activity__amount--credit' : 'activity__amount--debit'}`}
            >
              {formatAmount(entry)}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
