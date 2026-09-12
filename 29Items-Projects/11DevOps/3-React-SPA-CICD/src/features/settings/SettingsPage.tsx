import { useEffect, useState, type FormEvent } from 'react';

import { profileSchema, updateProfile, type Profile } from './settings.api';
import { LoadingSpinner } from '@/components/LoadingSpinner';
import { useFetch } from '@/hooks/useFetch';
import { trackEvent } from '@/lib/analytics/analytics';

type SaveState = 'idle' | 'saving' | 'saved' | 'error';

export function SettingsPage() {
  const { data, error, status, refetch } = useFetch<Profile>('/v1/settings/profile', {
    schema: profileSchema,
  });

  const [form, setForm] = useState<Profile | null>(null);
  const [saveState, setSaveState] = useState<SaveState>('idle');

  // Seed the form once the profile arrives (and after refetch).
  useEffect(() => {
    if (data) setForm(data);
  }, [data]);

  // Full-page states apply only before the form has data. Refetches (e.g. the re-sync
  // after a save) run in the background with the form still mounted — flashing the whole
  // page back to a spinner on every save is jarring.
  if (!form) {
    if (status === 'error') {
      return (
        <div role="alert" className="error-panel">
          <p>We could not load your settings{error ? ` (${error.code})` : ''}.</p>
          <button type="button" onClick={refetch}>
            Retry
          </button>
        </div>
      );
    }
    return <LoadingSpinner label="Loading your settings…" />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (!form || !data) return;

    // Send only what changed — the API contract is PATCH semantics.
    const patch: Partial<Profile> = {};
    if (form.displayName !== data.displayName) patch.displayName = form.displayName;
    if (form.marketingEmails !== data.marketingEmails) patch.marketingEmails = form.marketingEmails;
    if (form.productUpdates !== data.productUpdates) patch.productUpdates = form.productUpdates;

    if (Object.keys(patch).length === 0) {
      setSaveState('saved'); // nothing to do, but don't punish the user for clicking
      return;
    }

    setSaveState('saving');
    try {
      await updateProfile(patch);
      refetch(); // re-sync with the server's authoritative state
      setSaveState('saved');
      trackEvent('settings_saved', { fields_changed: Object.keys(patch).length });
    } catch {
      setSaveState('error');
    }
  }

  return (
    <section aria-labelledby="settings-heading" className="settings">
      <h1 id="settings-heading">Settings</h1>

      {saveState === 'saved' && (
        <p role="status" className="success-note">
          Your settings have been saved.
        </p>
      )}
      {saveState === 'error' && (
        <div role="alert" className="error-panel">
          Saving failed. Please try again.
        </div>
      )}

      {/* Controlled form is deliberate at three fields; adopt react-hook-form + zodResolver
          only when field count makes per-field state genuinely painful. */}
      <form onSubmit={(e) => void handleSubmit(e)}>
        <div className="form-field">
          <label htmlFor="displayName">Display name</label>
          <input
            id="displayName"
            type="text"
            maxLength={80}
            required
            value={form.displayName}
            onChange={(e) => setForm({ ...form, displayName: e.target.value })}
          />
        </div>

        <fieldset>
          <legend>E-mail notifications</legend>
          <div className="form-field form-field--checkbox">
            <input
              id="marketingEmails"
              type="checkbox"
              checked={form.marketingEmails}
              onChange={(e) => setForm({ ...form, marketingEmails: e.target.checked })}
            />
            <label htmlFor="marketingEmails">Marketing e-mails</label>
          </div>
          <div className="form-field form-field--checkbox">
            <input
              id="productUpdates"
              type="checkbox"
              checked={form.productUpdates}
              onChange={(e) => setForm({ ...form, productUpdates: e.target.checked })}
            />
            <label htmlFor="productUpdates">Product updates</label>
          </div>
        </fieldset>

        <button type="submit" disabled={saveState === 'saving'}>
          {saveState === 'saving' ? 'Saving…' : 'Save changes'}
        </button>
      </form>
    </section>
  );
}
