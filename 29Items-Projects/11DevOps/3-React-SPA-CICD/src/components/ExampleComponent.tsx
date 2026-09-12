import { announcementListSchema, type Announcement } from '@/api/types';
import { LoadingSpinner } from '@/components/LoadingSpinner';
import { useFetch } from '@/hooks/useFetch';

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Reference data-fetching component — the pattern every screen follows:
 *
 *   1. Fetch through useFetch (abortable, schema-validated).
 *   2. Render loading / error / empty / success as EXPLICIT states — no state
 *      is ever implied by the absence of another.
 *   3. Error state always offers a recovery affordance (Retry).
 *   4. Accessible by construction: role="alert" for errors, labelled regions,
 *      <time> for dates. Tests query by role/label, which keeps this honest.
 *
 * Tested in ExampleComponent.test.tsx against the real MSW mock API chain.
 * ═══════════════════════════════════════════════════════════════════════════
 */
export function ExampleComponent() {
  const { data, error, status, refetch } = useFetch<Announcement[]>('/v1/announcements', {
    schema: announcementListSchema,
  });

  if (status === 'idle' || status === 'loading') {
    return <LoadingSpinner label="Loading announcements…" />;
  }

  if (status === 'error') {
    return (
      <div role="alert" className="error-panel">
        <p>We could not load announcements{error ? ` (${error.code})` : ''}.</p>
        <button type="button" onClick={refetch}>
          Retry
        </button>
      </div>
    );
  }

  if (!data || data.length === 0) {
    return <p className="empty-state">No announcements right now — you're all caught up.</p>;
  }

  return (
    <section aria-labelledby="announcements-heading" className="announcements">
      <h2 id="announcements-heading">Announcements</h2>
      <ul>
        {data.map((announcement) => (
          <li key={announcement.id}>
            <article>
              <h3>{announcement.title}</h3>
              <p>{announcement.body}</p>
              <time dateTime={announcement.publishedAt}>
                {new Date(announcement.publishedAt).toLocaleDateString()}
              </time>
            </article>
          </li>
        ))}
      </ul>
    </section>
  );
}
