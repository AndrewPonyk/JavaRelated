import React, { useState, useEffect } from 'react';

interface CheckInResponse {
  accessGranted: boolean;
  status: 'GRANTED' | 'REJECTED';
  memberId?: string;
  memberName?: string;
  tier?: 'BASIC' | 'PREMIUM' | 'VIP';
  message: string;
  remainingGuestPasses?: number;
  currentCapacity: number;
  maxCapacity: number;
}

export const MemberCheckIn: React.FC = () => {
  const [badgeCode, setBadgeCode] = useState('');
  const [zone, setZone] = useState<'GYM_FLOOR' | 'POOL' | 'SAUNA' | 'VIP_LOUNGE' | 'CLASS_STUDIO'>('GYM_FLOOR');
  const [turnstileId] = useState('TURNSTILE-GATE-01');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<CheckInResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [occupancy, setOccupancy] = useState({ current: 42, max: 250 });

  // Fetch live capacity stats
  const fetchOccupancy = async () => {
    try {
      const res = await fetch('/api/v1/occupancy');
      if (res.ok) {
        const data = await res.json();
        setOccupancy({ current: data.currentOccupancy, max: data.maxCapacity });
      }
    } catch (err) {
      console.error('Failed to load occupancy data', err);
    }
  };

  useEffect(() => {
    fetchOccupancy();
    const interval = setInterval(fetchOccupancy, 10000);
    return () => clearInterval(interval);
  }, []);

  const handleScan = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!badgeCode.trim()) return;

    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const response = await fetch('/api/v1/check-in', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          badgeCode: badgeCode.trim().toUpperCase(),
          zone,
          turnstileId
        })
      });

      const data = await response.json();

      if (!response.ok) {
        // Anti-passback, frozen membership, or capacity exceeded
        setError(data.message || 'Access Denied');
      } else {
        setResult(data);
        setBadgeCode(''); // Clear input for next scan
        fetchOccupancy();
      }
    } catch (err: any) {
      setError(err.message || 'Network error scanning badge');
    } finally {
      setLoading(false);
    }
  };

  const occupancyPercentage = Math.min(100, Math.round((occupancy.current / occupancy.max) * 100));

  return (
    <div style={{ maxWidth: '650px', margin: '0 auto', fontFamily: 'system-ui, sans-serif', padding: '24px' }}>
      <header style={{ marginBottom: '24px', borderBottom: '2px solid #eaeaea', paddingBottom: '16px' }}>
        <h1 style={{ margin: '0 0 8px 0', color: '#1a1a1a' }}>Gym Turnstile Access Console</h1>
        <p style={{ margin: 0, color: '#666' }}>Gate: {turnstileId}</p>
      </header>

      {/* Live Occupancy Gauge */}
      <section style={{ background: '#f9fafb', padding: '16px', borderRadius: '8px', marginBottom: '24px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
          <span style={{ fontWeight: 600 }}>Facility Occupancy:</span>
          <span>{occupancy.current} / {occupancy.max} ({occupancyPercentage}%)</span>
        </div>
        <div style={{ width: '100%', height: '12px', background: '#e5e7eb', borderRadius: '6px', overflow: 'hidden' }}>
          <div 
            style={{
              width: `${occupancyPercentage}%`,
              height: '100%',
              backgroundColor: occupancyPercentage > 90 ? '#ef4444' : occupancyPercentage > 75 ? '#f59e0b' : '#10b981',
              transition: 'width 0.3s ease'
            }} 
          />
        </div>
      </section>

      {/* Badge Scanner Form */}
      <form onSubmit={handleScan} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <div>
          <label style={{ display: 'block', fontWeight: 600, marginBottom: '6px' }}>Target Zone</label>
          <select 
            value={zone} 
            onChange={(e) => setZone(e.target.value as any)}
            style={{ width: '100%', padding: '10px', borderRadius: '6px', border: '1px solid #ccc', fontSize: '15px' }}
          >
            <option value="GYM_FLOOR">Gym Floor (All Tiers)</option>
            <option value="POOL">Swimming Pool (Premium & VIP)</option>
            <option value="SAUNA">Sauna & Spa (Premium & VIP)</option>
            <option value="CLASS_STUDIO">Group Class Studio (Premium & VIP)</option>
            <option value="VIP_LOUNGE">VIP Lounge (VIP Only)</option>
          </select>
        </div>

        <div>
          <label style={{ display: 'block', fontWeight: 600, marginBottom: '6px' }}>Scan RFID / Barcode Badge</label>
          <input 
            type="text" 
            value={badgeCode}
            placeholder="Scan or type badge code (e.g. BADGE-VIP-001)"
            onChange={(e) => setBadgeCode(e.target.value)}
            autoFocus
            style={{ width: '100%', padding: '12px', fontSize: '18px', borderRadius: '6px', border: '2px solid #3b82f6', letterSpacing: '1px' }}
          />
        </div>

        <button 
          type="submit" 
          disabled={loading}
          style={{
            padding: '14px',
            backgroundColor: '#2563eb',
            color: '#fff',
            fontWeight: 600,
            fontSize: '16px',
            borderRadius: '6px',
            border: 'none',
            cursor: loading ? 'not-allowed' : 'pointer'
          }}
        >
          {loading ? 'Validating Access...' : 'Verify & Open Turnstile'}
        </button>
      </form>

      {/* Success Notification */}
      {result && (
        <div style={{ marginTop: '24px', padding: '16px', borderRadius: '8px', background: '#ecfdf5', border: '1px solid #a7f3d0' }}>
          <h3 style={{ color: '#065f46', margin: '0 0 8px 0' }}> ACCESS GRANTED</h3>
          <p style={{ margin: '4px 0', color: '#047857' }}><strong>Member:</strong> {result.memberName} ({result.tier})</p>
          <p style={{ margin: '4px 0', color: '#047857' }}>{result.message}</p>
          {result.remainingGuestPasses !== undefined && (
            <p style={{ margin: '4px 0', color: '#047857' }}><strong>Guest Passes Left:</strong> {result.remainingGuestPasses}</p>
          )}
        </div>
      )}

      {/* Error / Rejection / Anti-Passback Notification */}
      {error && (
        <div style={{ marginTop: '24px', padding: '16px', borderRadius: '8px', background: '#fef2f2', border: '1px solid #fecaca' }}>
          <h3 style={{ color: '#991b1b', margin: '0 0 8px 0' }}> ACCESS DENIED</h3>
          <p style={{ margin: 0, color: '#b91c1c' }}>{error}</p>
        </div>
      )}
    </div>
  );
};
