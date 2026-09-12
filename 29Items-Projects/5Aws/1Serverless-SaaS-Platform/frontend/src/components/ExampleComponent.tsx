import React from 'react';
import { UsageMetricsCard } from './dashboard/UsageMetricsCard';

/**
 * ExampleComponent demonstrating:
 * 1. Basic data fetching pattern (via useUsageMetrics hook)
 * 2. Display pattern for multi-tenant metrics & quota status
 * 3. Error and loading states
 */
export const ExampleComponent: React.FC = () => {
  return <UsageMetricsCard tenantId="tenant-alpha-enterprise" metric="api_calls" />;
};

export default ExampleComponent;
