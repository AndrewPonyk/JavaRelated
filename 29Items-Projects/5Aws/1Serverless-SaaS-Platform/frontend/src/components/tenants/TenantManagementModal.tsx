import React, { useState } from 'react';
import { createTenant, updateTenant } from '../../api/tenants';
import { Tenant, TenantTier } from '../../types/tenant';

interface TenantManagementModalProps {
  isOpen: boolean;
  onClose: () => void;
  onTenantSaved: (savedTenant: Tenant) => void;
  currentTenant: Tenant | null;
}

const AVAILABLE_COUNTRIES = ['US', 'CA', 'GB', 'DE', 'FR', 'JP', 'AU', 'BR', 'KP', 'IR'];

export const TenantManagementModal: React.FC<TenantManagementModalProps> = ({
  isOpen,
  onClose,
  onTenantSaved,
  currentTenant,
}) => {
  const isEditing = Boolean(currentTenant);

  const [name, setName] = useState(currentTenant?.name || '');
  const [tier, setTier] = useState<TenantTier>(currentTenant?.tier || 'STARTER');
  const [contactEmail, setContactEmail] = useState(currentTenant?.contact_email || '');
  const [monthlyQuota, setMonthlyQuota] = useState(currentTenant?.monthly_quota || 100000);
  const [selectedCountries, setSelectedCountries] = useState<string[]>(
    currentTenant?.allowed_countries || ['US', 'CA']
  );
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  if (!isOpen) return null;

  const toggleCountry = (country: string) => {
    if (selectedCountries.includes(country)) {
      if (selectedCountries.length === 1) {
        setValidationError('At least one country must be allowed in the geolocation whitelist.');
        return;
      }
      setSelectedCountries(selectedCountries.filter((c) => c !== country));
    } else {
      setSelectedCountries([...selectedCountries, country]);
    }
    setValidationError(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setValidationError(null);

    // Form validation
    if (!name.trim() || name.length < 2) {
      setValidationError('Tenant name must be at least 2 characters.');
      return;
    }
    if (!contactEmail.includes('@') || !contactEmail.includes('.')) {
      setValidationError('Please enter a valid email address.');
      return;
    }
    if (monthlyQuota < 1000) {
      setValidationError('Monthly quota must be at least 1,000 units.');
      return;
    }

    try {
      setIsSubmitting(true);
      let saved: Tenant;

      if (isEditing && currentTenant) {
        saved = await updateTenant(currentTenant.tenant_id, {
          name,
          tier,
          contact_email: contactEmail,
          monthly_quota: Number(monthlyQuota),
          allowed_countries: selectedCountries,
        });
      } else {
        saved = await createTenant({
          name,
          tier,
          contact_email: contactEmail,
          monthly_quota: Number(monthlyQuota),
          allowed_countries: selectedCountries,
        });
      }

      onTenantSaved(saved);
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save tenant';
      setValidationError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(0,0,0,0.7)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
      }}
    >
      <div
        className="card"
        style={{
          width: '100%',
          maxWidth: '520px',
          background: 'var(--bg-surface)',
          border: '1px solid var(--border-focus)',
        }}
      >
        <div className="card-header">
          <h3 className="card-title">{isEditing ? `Edit Tenant (${currentTenant?.tenant_id})` : 'Onboard New Tenant'}</h3>
          <button
            onClick={onClose}
            style={{
              background: 'transparent',
              border: 'none',
              color: 'var(--text-secondary)',
              cursor: 'pointer',
              fontSize: '1.25rem',
            }}
          >
            &times;
          </button>
        </div>

        {validationError && (
          <div
            style={{
              padding: '0.75rem',
              borderRadius: '0.375rem',
              background: 'rgba(239, 68, 68, 0.15)',
              border: '1px solid var(--danger)',
              color: 'var(--danger)',
              fontSize: '0.85rem',
              marginBottom: '1rem',
            }}
          >
            {validationError}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: '1rem' }}>
            <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '0.35rem' }}>
              Company / Tenant Name
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              style={{
                width: '100%',
                padding: '0.5rem 0.75rem',
                borderRadius: '0.375rem',
                background: 'var(--bg-surface-elevated)',
                border: '1px solid var(--border-subtle)',
                color: 'var(--text-primary)',
              }}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1rem' }}>
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '0.35rem' }}>
                Subscription Tier
              </label>
              <select
                value={tier}
                onChange={(e) => setTier(e.target.value as TenantTier)}
                style={{
                  width: '100%',
                  padding: '0.5rem 0.75rem',
                  borderRadius: '0.375rem',
                  background: 'var(--bg-surface-elevated)',
                  border: '1px solid var(--border-subtle)',
                  color: 'var(--text-primary)',
                }}
              >
                <option value="STARTER">STARTER ($49/mo)</option>
                <option value="PRO">PRO ($199/mo)</option>
                <option value="ENTERPRISE">ENTERPRISE ($899/mo)</option>
              </select>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '0.35rem' }}>
                Monthly Quota (Units)
              </label>
              <input
                type="number"
                value={monthlyQuota}
                onChange={(e) => setMonthlyQuota(Number(e.target.value))}
                min={1000}
                step={1000}
                required
                style={{
                  width: '100%',
                  padding: '0.5rem 0.75rem',
                  borderRadius: '0.375rem',
                  background: 'var(--bg-surface-elevated)',
                  border: '1px solid var(--border-subtle)',
                  color: 'var(--text-primary)',
                }}
              />
            </div>
          </div>

          <div style={{ marginBottom: '1rem' }}>
            <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '0.35rem' }}>
              Administrator Email
            </label>
            <input
              type="email"
              value={contactEmail}
              onChange={(e) => setContactEmail(e.target.value)}
              required
              style={{
                width: '100%',
                padding: '0.5rem 0.75rem',
                borderRadius: '0.375rem',
                background: 'var(--bg-surface-elevated)',
                border: '1px solid var(--border-subtle)',
                color: 'var(--text-primary)',
              }}
            />
          </div>

          <div style={{ marginBottom: '1.5rem' }}>
            <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '0.35rem' }}>
              Allowed Geolocation Whitelist (CloudFront Geo-Filter)
            </label>
            <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
              {AVAILABLE_COUNTRIES.map((c) => {
                const active = selectedCountries.includes(c);
                return (
                  <button
                    key={c}
                    type="button"
                    onClick={() => toggleCountry(c)}
                    style={{
                      padding: '0.25rem 0.6rem',
                      borderRadius: '0.35rem',
                      border: `1px solid ${active ? 'var(--accent-primary)' : 'var(--border-subtle)'}`,
                      background: active ? 'rgba(99, 102, 241, 0.2)' : 'var(--bg-surface-elevated)',
                      color: active ? 'var(--text-primary)' : 'var(--text-muted)',
                      cursor: 'pointer',
                      fontSize: '0.8rem',
                      fontFamily: 'var(--font-mono)',
                    }}
                  >
                    {c}
                  </button>
                );
              })}
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
            <button
              type="button"
              className="btn"
              onClick={onClose}
              style={{ background: 'var(--bg-surface-elevated)', color: 'var(--text-secondary)' }}
            >
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
              {isSubmitting ? 'Saving...' : isEditing ? 'Save Changes' : 'Create Tenant'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
