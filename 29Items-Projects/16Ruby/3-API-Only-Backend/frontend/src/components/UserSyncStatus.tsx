import { useEffect, useState } from 'react';
import { api, SyncStatus } from '../api/client';

interface Props {
  refreshKey: number;
}

export function UserSyncStatus({ refreshKey }: Props) {
  const [syncData, setSyncData] = useState<SyncStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    setError(null);

    api
      .syncStatus()
      .then((data) => {
        if (mounted) setSyncData(data);
      })
      .catch((err: Error) => {
        if (mounted) setError(err.message);
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [refreshKey]);

  if (loading) return <section className="panel">Loading sync status...</section>;
  if (error) return <section className="panel error">Error: {error}</section>;
  if (!syncData) return null;

  return (
    <section className="panel sync-status-card">
      <h3>Sync Information</h3>
      <p>Status: <strong className={`status ${syncData.status}`}>{syncData.status}</strong></p>
      <p>Last Sync: {syncData.lastSyncAt ? new Date(syncData.lastSyncAt).toLocaleString() : 'Not synced yet'}</p>
      <p>Records Synced: {syncData.totalRecords}</p>
      <p>Deleted Records: {syncData.deletedRecords}</p>
    </section>
  );
}
