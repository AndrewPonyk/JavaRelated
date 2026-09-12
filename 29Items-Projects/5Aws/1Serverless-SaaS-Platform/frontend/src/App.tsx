import React, { useEffect, useState } from 'react';
import { ErrorBoundary } from './components/common/ErrorBoundary';
import { UsageMetricsCard } from './components/dashboard/UsageMetricsCard';
import { CapacityPredictionChart } from './components/dashboard/CapacityPredictionChart';
import { TenantDetails } from './components/tenants/TenantDetails';
import { TenantManagementModal } from './components/tenants/TenantManagementModal';
import { listTenants } from './api/tenants';
import { Tenant } from './types/tenant';

export const App: React.FC = () => {
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState<string>(
    localStorage.getItem('saas_active_tenant') || 'tenant-alpha-enterprise'
  );
  const [simulatedCountry, setSimulatedCountry] = useState<string>(
    localStorage.getItem('saas_active_country') || 'US'
  );
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalTenant, setModalTenant] = useState<Tenant | null>(null);
  const [geoBlockedAlert, setGeoBlockedAlert] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const loadTenants = () => {
    listTenants()
      .then((res) => {
        if (res.tenants.length > 0) {
          setTenants(res.tenants);
        }
      })
      .catch((err) => console.warn('Could not list tenants', err));
  };

  useEffect(() => {
    loadTenants();
  }, []);

  const handleTenantChange = (tenantId: string) => {
    setSelectedTenantId(tenantId);
    localStorage.setItem('saas_active_tenant', tenantId);
    setGeoBlockedAlert(null);
    setRefreshKey((prev) => prev + 1);
  };

  const handleCountryChange = (country: string) => {
    setSimulatedCountry(country);
    localStorage.setItem('saas_active_country', country);

    // Check if the newly selected country is allowed for the active tenant
    const activeTenant = tenants.find((t) => t.tenant_id === selectedTenantId);
    if (activeTenant && !activeTenant.allowed_countries.includes(country)) {
      setGeoBlockedAlert(
        `Geolocation Policy Triggered: Access from '${country}' is denied by tenant '${activeTenant.name}' rules. Requests will return 403 Forbidden.`
      );
    } else {
      setGeoBlockedAlert(null);
    }
    setRefreshKey((prev) => prev + 1);
  };

  const openEditModal = () => {
    const current = tenants.find((t) => t.tenant_id === selectedTenantId) || null;
    setModalTenant(current);
    setIsModalOpen(true);
  };

  const openCreateModal = () => {
    setModalTenant(null);
    setIsModalOpen(true);
  };

  const handleTenantSaved = (savedTenant: Tenant) => {
    loadTenants();
    setSelectedTenantId(savedTenant.tenant_id);
    localStorage.setItem('saas_active_tenant', savedTenant.tenant_id);
    setRefreshKey((prev) => prev + 1);
  };

  return (
    <ErrorBoundary>
      <div className="app-container">
        {/* Top App Header */}
        <header className="app-header">
          <div>
            <div className="logo-badge">SERVERLESS SAAS PLATFORM</div>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
              Multi-Tenant Metering &bull; Tiered Billing &bull; ML Capacity Forecasting
            </p>
          </div>

          <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
            {/* Geolocation Simulator */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', background: 'var(--bg-surface-elevated)', padding: '0.35rem 0.75rem', borderRadius: '0.5rem' }}>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Simulate Geo:</span>
              <select
                value={simulatedCountry}
                onChange={(e) => handleCountryChange(e.target.value)}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: 'var(--text-primary)',
                  fontWeight: 600,
                  fontSize: '0.85rem',
                  fontFamily: 'var(--font-mono)',
                  cursor: 'pointer',
                }}
              >
                <option value="US" style={{ background: '#1f2937' }}>US (United States)</option>
                <option value="CA" style={{ background: '#1f2937' }}>CA (Canada)</option>
                <option value="GB" style={{ background: '#1f2937' }}>GB (United Kingdom)</option>
                <option value="DE" style={{ background: '#1f2937' }}>DE (Germany)</option>
                <option value="FR" style={{ background: '#1f2937' }}>FR (France)</option>
                <option value="KP" style={{ background: '#1f2937' }}>KP (North Korea - Blocked)</option>
                <option value="IR" style={{ background: '#1f2937' }}>IR (Iran - Blocked)</option>
              </select>
            </div>

            {/* Active Tenant Switcher */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', background: 'var(--bg-surface-elevated)', padding: '0.35rem 0.75rem', borderRadius: '0.5rem' }}>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Tenant:</span>
              <select
                value={selectedTenantId}
                onChange={(e) => handleTenantChange(e.target.value)}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: 'var(--text-primary)',
                  fontWeight: 600,
                  fontSize: '0.85rem',
                  cursor: 'pointer',
                }}
              >
                {tenants.map((t) => (
                  <option key={t.tenant_id} value={t.tenant_id} style={{ background: '#1f2937' }}>
                    {t.name} ({t.tier})
                  </option>
                ))}
              </select>
            </div>

            <button
              className="btn btn-primary"
              style={{ fontSize: '0.8rem', padding: '0.4rem 0.8rem' }}
              onClick={openCreateModal}
            >
              + Onboard Tenant
            </button>
          </div>
        </header>

        {/* Geolocation Alert Banner */}
        {geoBlockedAlert && (
          <div
            style={{
              padding: '0.85rem 1.25rem',
              borderRadius: '0.5rem',
              background: 'rgba(239, 68, 68, 0.15)',
              border: '1px solid var(--danger)',
              color: 'var(--danger)',
              fontSize: '0.9rem',
              marginBottom: '1.5rem',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <div>
              <strong>Geolocation Restricted (403):</strong> {geoBlockedAlert}
            </div>
            <button
              onClick={() => handleCountryChange('US')}
              style={{
                background: 'var(--danger)',
                color: '#fff',
                border: 'none',
                borderRadius: '0.25rem',
                padding: '0.25rem 0.5rem',
                fontSize: '0.75rem',
                cursor: 'pointer',
              }}
            >
              Reset to Allowed Region (US)
            </button>
          </div>
        )}

        {/* Main Dashboard Content */}
        <main key={refreshKey}>
          <div className="grid-cards">
            <UsageMetricsCard tenantId={selectedTenantId} metric="api_calls" />
            <CapacityPredictionChart tenantId={selectedTenantId} />
          </div>

          <TenantDetails
            tenantId={selectedTenantId}
            onEditClick={openEditModal}
            detectedCountry={simulatedCountry}
          />
        </main>

        <TenantManagementModal
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
          onTenantSaved={handleTenantSaved}
          currentTenant={modalTenant}
        />
      </div>
    </ErrorBoundary>
  );
};

export default App;
