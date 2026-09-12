import { useState } from 'react';

/**
 * Treatment UI for the 'onboarding-checklist' experiment (10 % canary — see the registry
 * in src/lib/analytics/abTesting.ts). The exposure event is logged by useExperiment at the
 * DashboardPage render site; conversion is measured offline via settings_saved / page_view
 * events in the ML regression pipeline (docs/ARCHITECTURE.md §2.3).
 */
const DISMISS_KEY = 'portal.onboarding-dismissed';

const CHECKLIST_ITEMS = [
  { id: 'profile', label: 'Complete your profile in Settings' },
  { id: 'notifications', label: 'Choose which e-mail notifications you want' },
  { id: 'dashboard', label: 'Review your balance and recent activity below' },
] as const;

function readDismissed(): boolean {
  try {
    return window.localStorage.getItem(DISMISS_KEY) === 'true';
  } catch {
    return false; // storage blocked (private mode) → just show the checklist
  }
}

export function OnboardingChecklist() {
  const [dismissed, setDismissed] = useState(readDismissed);

  if (dismissed) return null;

  function dismiss(): void {
    setDismissed(true);
    try {
      window.localStorage.setItem(DISMISS_KEY, 'true');
    } catch {
      // storage blocked — dismissal just won't survive a reload
    }
  }

  return (
    <aside aria-labelledby="onboarding-heading" className="onboarding">
      <div className="onboarding__header">
        <h2 id="onboarding-heading">Get started</h2>
        <button type="button" onClick={dismiss} aria-label="Dismiss getting-started checklist">
          ✕
        </button>
      </div>
      <ol>
        {CHECKLIST_ITEMS.map((item) => (
          <li key={item.id}>{item.label}</li>
        ))}
      </ol>
    </aside>
  );
}
