/**
 * Data Connection Dialog Component
 * Modal form for creating and editing database connections
 */

import React, { useState, useEffect } from 'react';
import { Button } from '@/components/ui/Button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/Card';
import { cn } from '@/utils/cn';
import {
  createDataConnection,
  updateDataConnection,
  testDataConnection,
  DataConnection,
  DataConnectionType,
  CreateDataConnectionInput,
  UpdateDataConnectionInput,
} from '@/services/api/dataConnectionApi';

interface DataConnectionDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (connection: DataConnection) => void;
  connection?: DataConnection; // If provided, edit mode
}

const connectionTypes: { value: DataConnectionType; label: string; icon: string }[] = [
  { value: 'POSTGRESQL', label: 'PostgreSQL', icon: '🐘' },
  { value: 'MYSQL', label: 'MySQL', icon: '🐬' },
];

const defaultPorts: Record<string, number> = {
  POSTGRESQL: 5432,
  MYSQL: 3306,
};

export function DataConnectionDialog({
  isOpen,
  onClose,
  onSuccess,
  connection,
}: DataConnectionDialogProps) {
  const isEditMode = !!connection;

  // Form state
  const [name, setName] = useState('');
  const [type, setType] = useState<DataConnectionType>('POSTGRESQL');
  const [host, setHost] = useState('');
  const [port, setPort] = useState(5432);
  const [database, setDatabase] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [ssl, setSsl] = useState(false);

  // UI state
  const [isLoading, setIsLoading] = useState(false);
  const [isTesting, setIsTesting] = useState(false);
  const [testResult, setTestResult] = useState<{
    success: boolean;
    latency?: number;
    version?: string;
    error?: string;
  } | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Reset form when dialog opens/closes or connection changes
  useEffect(() => {
    if (isOpen && connection) {
      setName(connection.name);
      setType(connection.type as DataConnectionType);
      setHost(connection.config.host);
      setPort(connection.config.port);
      setDatabase(connection.config.database);
      setSsl(connection.config.ssl || false);
      setUsername('');
      setPassword('');
    } else if (isOpen && !connection) {
      resetForm();
    }
    setTestResult(null);
    setError(null);
  }, [isOpen, connection]);

  // Update port when type changes
  useEffect(() => {
    if (!isEditMode) {
      setPort(defaultPorts[type] || 5432);
    }
  }, [type, isEditMode]);

  const resetForm = () => {
    setName('');
    setType('POSTGRESQL');
    setHost('');
    setPort(5432);
    setDatabase('');
    setUsername('');
    setPassword('');
    setSsl(false);
    setTestResult(null);
    setError(null);
  };

  const handleTest = async () => {
    if (!connection?.id) {
      setError('Save the connection first to test it');
      return;
    }

    setIsTesting(true);
    setTestResult(null);
    setError(null);

    try {
      const result = await testDataConnection(connection.id);
      setTestResult(result.data);
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to test connection';
      setTestResult({ success: false, error: errorMessage });
    } finally {
      setIsTesting(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError(null);

    try {
      if (isEditMode && connection) {
        // Update existing connection
        const updateInput: UpdateDataConnectionInput = {
          name,
          config: { host, port, database, ssl },
        };

        // Only include credentials if they were changed
        if (username || password) {
          updateInput.credentials = {};
          if (username) updateInput.credentials.username = username;
          if (password) updateInput.credentials.password = password;
        }

        const result = await updateDataConnection(connection.id, updateInput);
        onSuccess(result.data);
      } else {
        // Create new connection
        const createInput: CreateDataConnectionInput = {
          name,
          type,
          config: { host, port, database, ssl },
          credentials: { username, password },
        };

        const result = await createDataConnection(createInput);
        onSuccess(result.data);
      }

      onClose();
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to save connection';
      setError(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/50"
        onClick={onClose}
      />

      {/* Dialog */}
      <Card className="relative z-10 w-full max-w-lg max-h-[90vh] overflow-auto">
        <form onSubmit={handleSubmit}>
          <CardHeader>
            <CardTitle>
              {isEditMode ? 'Edit Data Connection' : 'New Data Connection'}
            </CardTitle>
            <CardDescription>
              {isEditMode
                ? 'Update connection settings. Leave password blank to keep existing.'
                : 'Connect to an external database to use as a data source.'}
            </CardDescription>
          </CardHeader>

          <CardContent className="space-y-4">
            {/* Connection Name */}
            <div className="space-y-2">
              <label className="text-sm font-medium">Connection Name</label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="My Database"
                className="w-full px-3 py-2 border rounded-md bg-background"
                required
              />
            </div>

            {/* Connection Type */}
            {!isEditMode && (
              <div className="space-y-2">
                <label className="text-sm font-medium">Database Type</label>
                <div className="grid grid-cols-2 gap-2">
                  {connectionTypes.map((ct) => (
                    <button
                      key={ct.value}
                      type="button"
                      onClick={() => setType(ct.value)}
                      className={cn(
                        'flex items-center gap-2 p-3 border rounded-md transition-colors',
                        type === ct.value
                          ? 'border-primary bg-primary/10'
                          : 'hover:border-primary/50'
                      )}
                    >
                      <span className="text-xl">{ct.icon}</span>
                      <span className="font-medium">{ct.label}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Host & Port */}
            <div className="grid grid-cols-3 gap-4">
              <div className="col-span-2 space-y-2">
                <label className="text-sm font-medium">Host</label>
                <input
                  type="text"
                  value={host}
                  onChange={(e) => setHost(e.target.value)}
                  placeholder="localhost"
                  className="w-full px-3 py-2 border rounded-md bg-background"
                  required
                />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">Port</label>
                <input
                  type="number"
                  value={port}
                  onChange={(e) => setPort(parseInt(e.target.value))}
                  className="w-full px-3 py-2 border rounded-md bg-background"
                  required
                />
              </div>
            </div>

            {/* Database */}
            <div className="space-y-2">
              <label className="text-sm font-medium">Database Name</label>
              <input
                type="text"
                value={database}
                onChange={(e) => setDatabase(e.target.value)}
                placeholder="mydb"
                className="w-full px-3 py-2 border rounded-md bg-background"
                required
              />
            </div>

            {/* Credentials */}
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">Username</label>
                <input
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder={isEditMode ? '(unchanged)' : 'postgres'}
                  className="w-full px-3 py-2 border rounded-md bg-background"
                  required={!isEditMode}
                />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">Password</label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder={isEditMode ? '(unchanged)' : '••••••••'}
                  className="w-full px-3 py-2 border rounded-md bg-background"
                  required={!isEditMode}
                />
              </div>
            </div>

            {/* SSL Toggle */}
            <div className="flex items-center gap-2">
              <input
                type="checkbox"
                id="ssl"
                checked={ssl}
                onChange={(e) => setSsl(e.target.checked)}
                className="h-4 w-4 rounded border"
              />
              <label htmlFor="ssl" className="text-sm font-medium">
                Use SSL/TLS
              </label>
            </div>

            {/* Test Result */}
            {testResult && (
              <div
                className={cn(
                  'p-3 rounded-md text-sm',
                  testResult.success
                    ? 'bg-green-500/10 text-green-700 dark:text-green-400'
                    : 'bg-red-500/10 text-red-700 dark:text-red-400'
                )}
              >
                {testResult.success ? (
                  <>
                    <div className="font-medium">Connection successful!</div>
                    <div className="text-xs mt-1">
                      Latency: {testResult.latency}ms
                      {testResult.version && ` • Version: ${testResult.version}`}
                    </div>
                  </>
                ) : (
                  <>
                    <div className="font-medium">Connection failed</div>
                    <div className="text-xs mt-1">{testResult.error}</div>
                  </>
                )}
              </div>
            )}

            {/* Error */}
            {error && (
              <div className="p-3 rounded-md bg-red-500/10 text-red-700 dark:text-red-400 text-sm">
                {error}
              </div>
            )}
          </CardContent>

          <CardFooter className="flex justify-between">
            <div>
              {isEditMode && connection && (
                <Button
                  type="button"
                  variant="outline"
                  onClick={handleTest}
                  disabled={isTesting}
                >
                  {isTesting ? 'Testing...' : 'Test Connection'}
                </Button>
              )}
            </div>
            <div className="flex gap-2">
              <Button type="button" variant="outline" onClick={onClose}>
                Cancel
              </Button>
              <Button type="submit" disabled={isLoading}>
                {isLoading ? 'Saving...' : isEditMode ? 'Update' : 'Create'}
              </Button>
            </div>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
}

export default DataConnectionDialog;
