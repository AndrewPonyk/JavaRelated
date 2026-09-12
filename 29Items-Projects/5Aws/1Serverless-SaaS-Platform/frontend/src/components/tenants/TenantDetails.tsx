import React, { useEffect, useState } from 'react';
import { fetchTenantProfile, listTenantUsers } from '../../api/tenants';
import { Tenant, TenantUser } from '../../types/tenant';
import { LoadingSpinner } from '../common/LoadingSpinner';

interface Props {
  tenantId: string;
  onEditClick: () => void;
  detectedCountry: string;
}

export const TenantDetails: React.FC<Props> = ({ tenantId, onEditClick, detectedCountry }) => {
  const [tenant, setTenant] = useState<Tenant | null>(null);
  const [users, setUsers] = useState<TenantUser[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    setIsLoading(true);
    Promise.all([fetchTenantProfile(tenantId), listTenantUsers(tenantId)])
      .then(([tData, uData]) => {
        setTenant(tData);
        setUsers(uData);
      })
      .catch((err) => console.error('Failed to load tenant details', err))
      .finally(() => setIsLoading(false));
  }, [tenantId]);

  if (isLoading) {
    return (
      <div className="card">
        <LoadingSpinner label="Loading tenant configuration..." />
      </div>
    );
  }

  if (!tenant) return null;

  const isCurrentCountryAllowed = tenant.allowed_countries.includes(detectedCountry);

  return (
    <div className="card">
      <div className="card-header">
        <div>
          <h3 className="card-title">Tenant Security &amp; Geolocation Policy</h3>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            Tenant ID: {tenant.tenant_id} &bull; Created: {tenant.created_at.substring(0, 10)}
          </span>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <span className="status-badge status-normal">{tenant.status}</span>
          <button
            className="btn btn-primary"
            style={{ fontSize: '0.8rem', padding: '0.35rem 0.75rem' }}
            onClick={onEditClick}
          >
            Edit Settings
          </button>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1.25rem', marginTop: '1rem' }}>
        <div>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Company / Account Name</span>
          <p style={{ fontWeight: 600, fontSize: '1.1rem' }}>{tenant.name}</p>
          <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Tier: {tenant.tier}</span>
        </div>

        <div>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            Allowed Geolocation Whitelist ({tenant.allowed_countries.length} regions)
          </span>
          <div style={{ display: 'flex', gap: '0.35rem', marginTop: '0.35rem', flexWrap: 'wrap' }}>
            {tenant.allowed_countries.map((c) => (
              <span
                key={c}
                style={{
                  padding: '0.2rem 0.5rem',
                  borderRadius: '0.25rem',
                  background: c === detectedCountry ? 'rgba(99, 102, 241, 0.3)' : 'var(--bg-surface-elevated)',
                  border: c === detectedCountry ? '1px solid var(--accent-primary)' : '1px solid var(--border-subtle)',
                  fontSize: '0.75rem',
                  fontFamily: 'var(--font-mono)',
                  color: c === detectedCountry ? 'var(--text-primary)' : 'var(--text-secondary)',
                }}
              >
                {c}
              </span>
            ))}
          </div>
          <div style={{ marginTop: '0.35rem', fontSize: '0.75rem' }}>
            Current Viewer Region ({detectedCountry}):{' '}
            <strong style={{ color: isCurrentCountryAllowed ? 'var(--success)' : 'var(--danger)' }}>
              {isCurrentCountryAllowed ? 'AUTHORIZED' : 'RESTRICTED (403)'}
            </strong>
          </div>
        </div>

        <div>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Team Members ({users.length})</span>
          <p style={{ fontSize: '0.875rem', fontWeight: 500, marginTop: '0.25rem' }}>{tenant.contact_email} (Primary)</p>
          {users.map((u) => (
            <div key={u.user_id} style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
              {u.name} &bull; <span style={{ fontFamily: 'var(--font-mono)' }}>{u.role}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
