import React, { useState, useEffect } from 'react';
import { Database, Plus, RefreshCw, CheckCircle, XCircle, AlertCircle, MoreVertical, Edit, Trash2, Play, Pause, Clock } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/DropdownMenu';
import { DataConnectionDialog } from '@/components/data-sources/DataConnectionDialog';
import { useToastHelpers } from '@/components/ui/Toaster';
import { cn } from '@/utils/cn';
import {
  getDataConnections,
  deleteDataConnection,
  testDataConnection,
  DataConnection,
  DataConnectionType,
} from '@/services/api/dataConnectionApi';

const dataSourceTypeInfo: Record<DataConnectionType, { name: string; icon: string }> = {
  POSTGRESQL: { name: 'PostgreSQL', icon: '🐘' },
  MYSQL: { name: 'MySQL', icon: '🐬' },
  MONGODB: { name: 'MongoDB', icon: '🍃' },
  REST_API: { name: 'REST API', icon: '🔗' },
  GRAPHQL_API: { name: 'GraphQL', icon: '◈' },
  CSV_FILE: { name: 'CSV/Excel', icon: '📊' },
  JSON_FILE: { name: 'JSON File', icon: '📄' },
  GOOGLE_SHEETS: { name: 'Google Sheets', icon: '📗' },
  SALESFORCE: { name: 'Salesforce', icon: '☁️' },
  HUBSPOT: { name: 'HubSpot', icon: '🔶' },
  SLACK: { name: 'Slack', icon: '💬' },
  CUSTOM: { name: 'Custom', icon: '🔧' },
};

export const DataSources: React.FC = () => {
  const [dataSources, setDataSources] = useState<DataConnection[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [showConnectionDialog, setShowConnectionDialog] = useState(false);
  const [editingConnection, setEditingConnection] = useState<DataConnection | undefined>();
  const [testingId, setTestingId] = useState<string | null>(null);
  const { success: showSuccess, error: showError } = useToastHelpers();

  useEffect(() => {
    loadDataSources();
  }, []);

  const loadDataSources = async () => {
    setIsLoading(true);
    try {
      const result = await getDataConnections({ limit: 100 });
      setDataSources(result.data);
    } catch (err) {
      showError('Failed to load', 'Could not load data connections');
    } finally {
      setIsLoading(false);
    }
  };

  const handleAddConnection = () => {
    setEditingConnection(undefined);
    setShowConnectionDialog(true);
  };

  const handleEditConnection = (connection: DataConnection) => {
    setEditingConnection(connection);
    setShowConnectionDialog(true);
  };

  const handleDeleteConnection = async (id: string) => {
    if (!confirm('Are you sure you want to delete this connection?')) return;

    try {
      await deleteDataConnection(id);
      setDataSources(dataSources.filter(d => d.id !== id));
      showSuccess('Deleted', 'Data connection has been deleted');
    } catch (err) {
      showError('Delete failed', 'Could not delete data connection');
    }
  };

  const handleTestConnection = async (id: string) => {
    setTestingId(id);
    try {
      const result = await testDataConnection(id);
      if (result.data.success) {
        showSuccess('Connection successful', `Latency: ${result.data.latency}ms`);
        // Refresh to get updated status
        loadDataSources();
      } else {
        showError('Connection failed', result.data.error || 'Unknown error');
      }
    } catch (err) {
      showError('Test failed', 'Could not test connection');
    } finally {
      setTestingId(null);
    }
  };

  const handleConnectionSuccess = (connection: DataConnection) => {
    if (editingConnection) {
      // Update existing
      setDataSources(dataSources.map(d => d.id === connection.id ? connection : d));
    } else {
      // Add new
      setDataSources([connection, ...dataSources]);
    }
    setShowConnectionDialog(false);
    setEditingConnection(undefined);
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'CONNECTED': return <CheckCircle className="h-4 w-4 text-green-500" />;
      case 'PENDING': return <Clock className="h-4 w-4 text-yellow-500" />;
      case 'FAILED': return <XCircle className="h-4 w-4 text-red-500" />;
      case 'DISABLED': return <Pause className="h-4 w-4 text-gray-500" />;
      default: return <AlertCircle className="h-4 w-4 text-gray-500" />;
    }
  };

  const getStatusBadge = (status: string) => {
    const styles: Record<string, string> = {
      CONNECTED: 'bg-green-100 text-green-800',
      PENDING: 'bg-yellow-100 text-yellow-800',
      FAILED: 'bg-red-100 text-red-800',
      DISABLED: 'bg-gray-100 text-gray-800',
    };
    return <Badge className={styles[status] || 'bg-gray-100'}>{status.toLowerCase()}</Badge>;
  };

  const formatLastTested = (date?: string) => {
    if (!date) return 'Never';
    const d = new Date(date);
    const now = new Date();
    const diff = now.getTime() - d.getTime();
    const minutes = Math.floor(diff / 60000);
    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes} min ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours} hour${hours > 1 ? 's' : ''} ago`;
    return d.toLocaleDateString();
  };

  // Stats
  const stats = {
    total: dataSources.length,
    connected: dataSources.filter(d => d.status === 'CONNECTED').length,
    pending: dataSources.filter(d => d.status === 'PENDING').length,
    failed: dataSources.filter(d => d.status === 'FAILED').length,
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Data Sources</h1>
          <p className="text-gray-600">Connect and manage your database connections</p>
        </div>
        <Button onClick={handleAddConnection}>
          <Plus className="mr-2 h-4 w-4" />
          Add Data Source
        </Button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {[
          { label: 'Total Sources', value: stats.total, color: 'blue' },
          { label: 'Connected', value: stats.connected, color: 'green' },
          { label: 'Pending', value: stats.pending, color: 'yellow' },
          { label: 'Failed', value: stats.failed, color: 'red' },
        ].map((stat, i) => (
          <Card key={i} className="p-4">
            <p className="text-sm text-gray-500">{stat.label}</p>
            <p className="text-2xl font-bold text-gray-900">{stat.value}</p>
          </Card>
        ))}
      </div>

      {/* Loading State */}
      {isLoading && (
        <div className="flex items-center justify-center py-12">
          <RefreshCw className="h-8 w-8 animate-spin text-gray-400" />
        </div>
      )}

      {/* Empty State */}
      {!isLoading && dataSources.length === 0 && (
        <Card className="p-8 text-center">
          <Database className="h-12 w-12 mx-auto text-gray-400 mb-4" />
          <h3 className="text-lg font-medium text-gray-900 mb-2">No data sources yet</h3>
          <p className="text-gray-600 mb-4">Connect to a database to start building queries and powering your widgets.</p>
          <Button onClick={handleAddConnection}>
            <Plus className="mr-2 h-4 w-4" />
            Add Your First Data Source
          </Button>
        </Card>
      )}

      {/* Data Sources Grid */}
      {!isLoading && dataSources.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {dataSources.map((source) => {
            const typeInfo = dataSourceTypeInfo[source.type] || { name: source.type, icon: '📦' };
            const isTesting = testingId === source.id;

            return (
              <Card key={source.id} className="p-4">
                <div className="flex items-start justify-between">
                  <div className="flex items-center space-x-3">
                    <div className="text-2xl">{typeInfo.icon}</div>
                    <div>
                      <h3 className="font-medium text-gray-900">{source.name}</h3>
                      <p className="text-sm text-gray-500">{typeInfo.name}</p>
                    </div>
                  </div>
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="icon">
                        <MoreVertical className="h-4 w-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onClick={() => handleTestConnection(source.id)}>
                        <Play className="mr-2 h-4 w-4" />
                        Test Connection
                      </DropdownMenuItem>
                      <DropdownMenuItem onClick={() => handleEditConnection(source)}>
                        <Edit className="mr-2 h-4 w-4" />
                        Edit
                      </DropdownMenuItem>
                      <DropdownMenuItem
                        className="text-red-600"
                        onClick={() => handleDeleteConnection(source.id)}
                      >
                        <Trash2 className="mr-2 h-4 w-4" />
                        Delete
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>

                <div className="mt-4 space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-gray-500">Status</span>
                    <div className="flex items-center gap-2">
                      {getStatusIcon(source.status)}
                      {getStatusBadge(source.status)}
                    </div>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-gray-500">Host</span>
                    <span className="text-sm text-gray-700 font-mono">
                      {source.config.host}:{source.config.port}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-gray-500">Database</span>
                    <span className="text-sm text-gray-700 font-mono">
                      {source.config.database}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-gray-500">Last Tested</span>
                    <span className="text-sm text-gray-700">
                      {formatLastTested(source.lastTested)}
                    </span>
                  </div>
                </div>

                <div className="mt-4 pt-4 border-t flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    className="flex-1"
                    onClick={() => handleTestConnection(source.id)}
                    disabled={isTesting}
                  >
                    {isTesting ? (
                      <>
                        <RefreshCw className="mr-1 h-3 w-3 animate-spin" />
                        Testing...
                      </>
                    ) : (
                      <>
                        <Play className="mr-1 h-3 w-3" />
                        Test
                      </>
                    )}
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    className="flex-1"
                    onClick={() => handleEditConnection(source)}
                  >
                    <Edit className="mr-1 h-3 w-3" />
                    Edit
                  </Button>
                </div>
              </Card>
            );
          })}

          {/* Add New Card */}
          <Card
            className="p-4 border-2 border-dashed border-gray-300 hover:border-blue-400 cursor-pointer transition-colors"
            onClick={handleAddConnection}
          >
            <div className="h-full flex flex-col items-center justify-center text-gray-500 py-8">
              <Plus className="h-8 w-8 mb-2" />
              <p className="font-medium">Add Data Source</p>
              <p className="text-sm">Connect PostgreSQL or MySQL</p>
            </div>
          </Card>
        </div>
      )}

      {/* Connection Dialog */}
      <DataConnectionDialog
        isOpen={showConnectionDialog}
        onClose={() => {
          setShowConnectionDialog(false);
          setEditingConnection(undefined);
        }}
        onSuccess={handleConnectionSuccess}
        connection={editingConnection}
      />
    </div>
  );
};

export default DataSources;
