import { AccountSummaryCard } from './components/AccountSummaryCard';
import { ActivityFeed } from './components/ActivityFeed';
import { BalanceTrendCard } from './components/BalanceTrendCard';
import { OnboardingChecklist } from './components/OnboardingChecklist';
import { useDashboardData } from './useDashboardData';
import { ExampleComponent } from '@/components/ExampleComponent';
import { LoadingSpinner } from '@/components/LoadingSpinner';
import { useAuth } from '@/features/auth/useAuth';
import { useExperiment } from '@/lib/analytics/abTesting';

export function DashboardPage() {
  const { user } = useAuth();
  const subjectId = user?.id ?? 'anonymous';

  // A/B experiments: assignment is deterministic per user; exposure events fire at this
  // render site — exactly when the variant UI is shown (docs/ARCHITECTURE.md §2.3).
  const layoutVariant = useExperiment('dashboard-layout-v2', subjectId);
  const onboardingVariant = useExperiment('onboarding-checklist', subjectId);

  const { data, error, status, refetch } = useDashboardData();

  return (
    <div className={`dashboard dashboard--${layoutVariant}`}>
      <h1>Welcome back{user ? `, ${user.displayName}` : ''}</h1>

      {onboardingVariant === 'treatment' && <OnboardingChecklist />}

      {status === 'idle' || status === 'loading' ? (
        <LoadingSpinner label="Loading your account summary…" />
      ) : status === 'error' ? (
        <div role="alert" className="error-panel">
          <p>We could not load your account summary{error ? ` (${error.code})` : ''}.</p>
          <button type="button" onClick={refetch}>
            Retry
          </button>
        </div>
      ) : data ? (
        <section aria-label="Account summary" className="summary-grid">
          <AccountSummaryCard
            label="Balance"
            value={new Intl.NumberFormat(undefined, {
              style: 'currency',
              currency: data.currency,
            }).format(data.balance)}
          />
          <AccountSummaryCard
            label="Open tickets"
            value={String(data.openTickets)}
            hint={data.openTickets === 0 ? 'All resolved' : 'Awaiting response'}
          />
          <AccountSummaryCard
            label="Last sign-in"
            value={
              data.lastLoginAt
                ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(
                    new Date(data.lastLoginAt),
                  )
                : 'First visit'
            }
          />
          <BalanceTrendCard history={data.balanceHistory} currency={data.currency} />
        </section>
      ) : null}

      <ActivityFeed />

      {/* Announcements — also the codebase's reference fetching pattern. */}
      <ExampleComponent />
    </div>
  );
}
