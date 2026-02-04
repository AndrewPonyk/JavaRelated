/**
 * Widget Data Configuration Component
 * Configure data binding for widgets - select data source, build query, map fields
 */

import React, { useState, useEffect } from 'react';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { cn } from '@/utils/cn';
import { QueryBuilder } from '@/components/data-sources/QueryBuilder';
import { QueryPreview } from '@/components/data-sources/QueryPreview';
import {
  getDataConnections,
  DataConnection,
  VisualQuery,
} from '@/services/api/dataConnectionApi';

export interface DataBindingConfig {
  connectionId: string;
  queryType: 'raw' | 'visual';
  rawQuery?: string;
  visualQuery?: VisualQuery;
  refreshInterval?: number;
  fieldMappings?: Record<string, string>;
}

interface WidgetDataConfigProps {
  widgetId: string;
  widgetType: string;
  initialConfig?: DataBindingConfig;
  onSave: (config: DataBindingConfig) => void;
  onCancel: () => void;
}

const refreshIntervals = [
  { value: 0, label: 'Manual only' },
  { value: 30, label: '30 seconds' },
  { value: 60, label: '1 minute' },
  { value: 300, label: '5 minutes' },
  { value: 900, label: '15 minutes' },
  { value: 1800, label: '30 minutes' },
  { value: 3600, label: '1 hour' },
];

// Widget type to expected field mapping
const widgetFieldSuggestions: Record<string, string[]> = {
  METRIC: ['value', 'label', 'change', 'trend'],
  CHART_LINE: ['x', 'y', 'series', 'label'],
  CHART_BAR: ['x', 'y', 'category', 'label'],
  CHART_PIE: ['value', 'label', 'category'],
  TABLE: [], // Tables use all columns
  default: ['value', 'label'],
};

export function WidgetDataConfig({
  widgetId,
  widgetType,
  initialConfig,
  onSave,
  onCancel,
}: WidgetDataConfigProps) {
  // Data state
  const [connections, setConnections] = useState<DataConnection[]>([]);
  const [isLoadingConnections, setIsLoadingConnections] = useState(false);

  // Config state
  const [connectionId, setConnectionId] = useState(initialConfig?.connectionId || '');
  const [queryType, setQueryType] = useState<'visual' | 'raw'>(
    initialConfig?.queryType || 'visual'
  );
  const [visualQuery, setVisualQuery] = useState<VisualQuery | undefined>(
    initialConfig?.visualQuery
  );
  const [rawQuery, setRawQuery] = useState(initialConfig?.rawQuery || '');
  const [refreshInterval, setRefreshInterval] = useState(
    initialConfig?.refreshInterval || 0
  );
  const [fieldMappings, setFieldMappings] = useState<Record<string, string>>(
    initialConfig?.fieldMappings || {}
  );

  // UI state
  const [activeTab, setActiveTab] = useState<'source' | 'query' | 'mapping'>('source');
  const [showPreview, setShowPreview] = useState(false);

  // Load connections on mount
  useEffect(() => {
    loadConnections();
  }, []);

  const loadConnections = async () => {
    setIsLoadingConnections(true);
    try {
      const result = await getDataConnections({ limit: 100 });
      setConnections(result.data.filter((c) => c.status === 'CONNECTED'));
    } catch (error) {
      console.error('Failed to load connections:', error);
    } finally {
      setIsLoadingConnections(false);
    }
  };

  const handleSave = () => {
    const config: DataBindingConfig = {
      connectionId,
      queryType,
      refreshInterval,
      fieldMappings: Object.keys(fieldMappings).length > 0 ? fieldMappings : undefined,
    };

    if (queryType === 'visual' && visualQuery) {
      config.visualQuery = visualQuery;
    } else if (queryType === 'raw' && rawQuery) {
      config.rawQuery = rawQuery;
    }

    onSave(config);
  };

  const updateFieldMapping = (widgetField: string, queryColumn: string) => {
    if (queryColumn) {
      setFieldMappings((prev) => ({ ...prev, [widgetField]: queryColumn }));
    } else {
      setFieldMappings((prev) => {
        const newMappings = { ...prev };
        delete newMappings[widgetField];
        return newMappings;
      });
    }
  };

  const suggestedFields = widgetFieldSuggestions[widgetType] || widgetFieldSuggestions.default;

  // Get available columns from visual query
  const availableColumns: string[] =
    visualQuery?.columns.map((c) => c.alias || c.column) || [];

  const selectedConnection = connections.find((c) => c.id === connectionId);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/50" onClick={onCancel} />

      {/* Dialog */}
      <div className="relative z-10 w-full max-w-4xl max-h-[90vh] overflow-auto bg-background rounded-lg shadow-lg">
        {/* Header */}
        <div className="sticky top-0 bg-background border-b p-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Configure Widget Data</h2>
          <div className="flex items-center gap-2">
            <Button variant="outline" onClick={onCancel}>
              Cancel
            </Button>
            <Button onClick={handleSave} disabled={!connectionId}>
              Save Configuration
            </Button>
          </div>
        </div>

        {/* Tabs */}
        <div className="border-b">
          <div className="flex">
            <button
              type="button"
              onClick={() => setActiveTab('source')}
              className={cn(
                'px-4 py-2 text-sm font-medium border-b-2 transition-colors',
                activeTab === 'source'
                  ? 'border-primary text-primary'
                  : 'border-transparent hover:text-primary'
              )}
            >
              1. Data Source
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('query')}
              className={cn(
                'px-4 py-2 text-sm font-medium border-b-2 transition-colors',
                activeTab === 'query'
                  ? 'border-primary text-primary'
                  : 'border-transparent hover:text-primary',
                !connectionId && 'opacity-50 cursor-not-allowed'
              )}
              disabled={!connectionId}
            >
              2. Build Query
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('mapping')}
              className={cn(
                'px-4 py-2 text-sm font-medium border-b-2 transition-colors',
                activeTab === 'mapping'
                  ? 'border-primary text-primary'
                  : 'border-transparent hover:text-primary',
                !connectionId && 'opacity-50 cursor-not-allowed'
              )}
              disabled={!connectionId}
            >
              3. Field Mapping
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="p-4">
          {/* Data Source Tab */}
          {activeTab === 'source' && (
            <div className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Select Data Connection</CardTitle>
                </CardHeader>
                <CardContent>
                  {isLoadingConnections ? (
                    <div className="text-sm text-muted-foreground">Loading connections...</div>
                  ) : connections.length === 0 ? (
                    <div className="text-sm text-muted-foreground">
                      No connected data sources. Create a data connection first.
                    </div>
                  ) : (
                    <div className="grid grid-cols-2 gap-3">
                      {connections.map((conn) => (
                        <button
                          key={conn.id}
                          type="button"
                          onClick={() => setConnectionId(conn.id)}
                          className={cn(
                            'p-4 border rounded-lg text-left transition-colors',
                            connectionId === conn.id
                              ? 'border-primary bg-primary/10'
                              : 'hover:border-primary/50'
                          )}
                        >
                          <div className="flex items-center gap-2">
                            <span className="text-xl">
                              {conn.type === 'POSTGRESQL' ? '🐘' : '🐬'}
                            </span>
                            <div>
                              <div className="font-medium">{conn.name}</div>
                              <div className="text-xs text-muted-foreground">
                                {conn.type}
                              </div>
                            </div>
                          </div>
                        </button>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Refresh Settings</CardTitle>
                </CardHeader>
                <CardContent>
                  <select
                    value={refreshInterval}
                    onChange={(e) => setRefreshInterval(parseInt(e.target.value))}
                    className="w-full px-3 py-2 border rounded-md bg-background"
                  >
                    {refreshIntervals.map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                  <p className="mt-2 text-xs text-muted-foreground">
                    How often should this widget refresh its data automatically?
                  </p>
                </CardContent>
              </Card>

              {connectionId && (
                <div className="flex justify-end">
                  <Button onClick={() => setActiveTab('query')}>
                    Next: Build Query
                  </Button>
                </div>
              )}
            </div>
          )}

          {/* Query Tab */}
          {activeTab === 'query' && connectionId && (
            <div className="space-y-4">
              {/* Query Type Toggle */}
              <div className="flex items-center gap-4">
                <span className="text-sm font-medium">Query Mode:</span>
                <div className="flex border rounded-md">
                  <button
                    type="button"
                    onClick={() => setQueryType('visual')}
                    className={cn(
                      'px-4 py-2 text-sm transition-colors',
                      queryType === 'visual'
                        ? 'bg-primary text-primary-foreground'
                        : 'hover:bg-muted'
                    )}
                  >
                    Visual Builder
                  </button>
                  <button
                    type="button"
                    onClick={() => setQueryType('raw')}
                    className={cn(
                      'px-4 py-2 text-sm transition-colors',
                      queryType === 'raw'
                        ? 'bg-primary text-primary-foreground'
                        : 'hover:bg-muted'
                    )}
                  >
                    Raw SQL
                  </button>
                </div>
              </div>

              {/* Query Builder or Raw SQL */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  {queryType === 'visual' ? (
                    <QueryBuilder
                      connectionId={connectionId}
                      initialQuery={visualQuery}
                      onChange={setVisualQuery}
                      onPreview={() => setShowPreview(true)}
                    />
                  ) : (
                    <Card>
                      <CardHeader>
                        <CardTitle className="text-base">Raw SQL Query</CardTitle>
                      </CardHeader>
                      <CardContent>
                        <textarea
                          value={rawQuery}
                          onChange={(e) => setRawQuery(e.target.value)}
                          placeholder="SELECT * FROM your_table LIMIT 100"
                          className="w-full h-64 px-3 py-2 font-mono text-sm border rounded-md bg-background"
                        />
                        <p className="mt-2 text-xs text-muted-foreground">
                          Only SELECT queries are allowed. Dangerous keywords are blocked.
                        </p>
                      </CardContent>
                    </Card>
                  )}
                </div>

                <div>
                  <QueryPreview
                    connectionId={connectionId}
                    visualQuery={queryType === 'visual' ? visualQuery : undefined}
                    rawQuery={queryType === 'raw' ? rawQuery : undefined}
                    queryType={queryType}
                    onQueryChange={queryType === 'raw' ? setRawQuery : undefined}
                  />
                </div>
              </div>

              <div className="flex justify-between">
                <Button variant="outline" onClick={() => setActiveTab('source')}>
                  Back
                </Button>
                <Button
                  onClick={() => setActiveTab('mapping')}
                  disabled={queryType === 'visual' ? !visualQuery : !rawQuery}
                >
                  Next: Field Mapping
                </Button>
              </div>
            </div>
          )}

          {/* Field Mapping Tab */}
          {activeTab === 'mapping' && connectionId && (
            <div className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Map Query Columns to Widget Fields</CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-sm text-muted-foreground mb-4">
                    Map the columns from your query to the fields expected by the {widgetType} widget.
                  </p>

                  {suggestedFields.length > 0 ? (
                    <div className="space-y-3">
                      {suggestedFields.map((field) => (
                        <div key={field} className="flex items-center gap-4">
                          <label className="w-32 text-sm font-medium">{field}</label>
                          <select
                            value={fieldMappings[field] || ''}
                            onChange={(e) => updateFieldMapping(field, e.target.value)}
                            className="flex-1 px-3 py-2 border rounded-md bg-background"
                          >
                            <option value="">-- Select column --</option>
                            {availableColumns.map((col) => (
                              <option key={col} value={col}>
                                {col}
                              </option>
                            ))}
                          </select>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <p className="text-sm text-muted-foreground">
                      Table widgets use all columns from the query. No field mapping needed.
                    </p>
                  )}
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Configuration Summary</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-2 text-sm">
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Data Source:</span>
                      <span className="font-medium">{selectedConnection?.name}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Query Type:</span>
                      <span className="font-medium capitalize">{queryType}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Auto Refresh:</span>
                      <span className="font-medium">
                        {refreshIntervals.find((r) => r.value === refreshInterval)?.label}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Field Mappings:</span>
                      <span className="font-medium">
                        {Object.keys(fieldMappings).length} configured
                      </span>
                    </div>
                  </div>
                </CardContent>
              </Card>

              <div className="flex justify-between">
                <Button variant="outline" onClick={() => setActiveTab('query')}>
                  Back
                </Button>
                <Button onClick={handleSave}>Save Configuration</Button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default WidgetDataConfig;
