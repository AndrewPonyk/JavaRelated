import React, { useEffect, useState } from 'react';

interface EdgeMetrics {
    node_id: string;
    request_count: number;
    cache_hit_rate: number;
    latency_ms: number;
}

export const EdgeStatusDashboard: React.FC = () => {
    const [metrics, setMetrics] = useState<EdgeMetrics[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchMetrics = async () => {
            try {
                const response = await fetch('/api/metrics');
                if (!response.ok) throw new Error('Failed to fetch metrics');
                const data = await response.json();
                setMetrics(data);
            } catch (err: any) {
                setError(err.message);
            } finally {
                setLoading(false);
            }
        };

        fetchMetrics();
    }, []);

    if (loading) return <div>Loading edge status...</div>;
    if (error) return <div className="error-state">Error: {error}</div>;

    return (
        <div className="dashboard-container">
            <h2>Edge Node Status</h2>
            <div className="metrics-grid">
                {metrics.map(node => (
                    <div key={node.node_id} className="metric-card">
                        <h3>Node: {node.node_id}</h3>
                        <p>Requests: {node.request_count}</p>
                        <p>Cache Hit Rate: {node.cache_hit_rate.toFixed(2)}%</p>
                        <p>Latency: {node.latency_ms}ms</p>
                    </div>
                ))}
            </div>
        </div>
    );
};
