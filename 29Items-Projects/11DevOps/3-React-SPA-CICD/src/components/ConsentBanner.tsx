import { useState } from 'react';

import {
  getStoredConsent,
  isAnalyticsConfigured,
  setAnalyticsConsent,
  type ConsentDecision,
} from '@/lib/analytics/analytics';

/**
 * Minimal analytics-consent banner (docs/ARCHITECTURE.md §2.5): shown only when a GA
 * measurement id is configured AND the user hasn't decided yet. Until "Allow" is chosen,
 * gtag.js is never loaded and analytics_storage stays denied.
 */
export function ConsentBanner() {
  const [decided, setDecided] = useState(
    () => !isAnalyticsConfigured() || getStoredConsent() !== null,
  );

  if (decided) return null;

  function decide(decision: ConsentDecision): void {
    setAnalyticsConsent(decision);
    setDecided(true);
  }

  return (
    <div className="consent-banner" role="region" aria-label="Analytics consent">
      <p>
        We use anonymous usage analytics to improve the portal. No personal data is collected
        without your permission.
      </p>
      <div className="consent-banner__actions">
        <button type="button" onClick={() => decide('granted')}>
          Allow analytics
        </button>
        <button type="button" className="consent-banner__decline" onClick={() => decide('denied')}>
          Decline
        </button>
      </div>
    </div>
  );
}
