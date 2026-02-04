import React, { useState } from 'react';
import { FileText, Search, Filter, Download, ChevronLeft, ChevronRight, User, Settings, Database, Shield } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { cn } from '@/utils/cn';

const mockLogs = [
  { id: '1', action: 'User Login', user: 'admin@example.com', category: 'auth', ip: '192.168.1.1', timestamp: '2024-01-15 14:30:00', status: 'success' },
  { id: '2', action: 'Dashboard Created', user: 'john@example.com', category: 'dashboard', ip: '192.168.1.2', timestamp: '2024-01-15 14:25:00', status: 'success' },
  { id: '3', action: 'Settings Updated', user: 'admin@example.com', category: 'settings', ip: '192.168.1.1', timestamp: '2024-01-15 14:20:00', status: 'success' },
  { id: '4', action: 'Failed Login Attempt', user: 'unknown', category: 'auth', ip: '10.0.0.55', timestamp: '2024-01-15 14:15:00', status: 'failed' },
  { id: '5', action: 'User Role Changed', user: 'admin@example.com', category: 'user', ip: '192.168.1.1', timestamp: '2024-01-15 14:10:00', status: 'success' },
  { id: '6', action: 'Data Export', user: 'jane@example.com', category: 'data', ip: '192.168.1.3', timestamp: '2024-01-15 14:05:00', status: 'success' },
  { id: '7', action: 'API Key Generated', user: 'admin@example.com', category: 'security', ip: '192.168.1.1', timestamp: '2024-01-15 14:00:00', status: 'success' },
];

const categories = ['All', 'auth', 'dashboard', 'settings', 'user', 'data', 'security'];

export const AuditLogs: React.FC = () => {
  const [logs] = useState(mockLogs);
  const [search, setSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('All');
  const [page, setPage] = useState(1);

  const filteredLogs = logs.filter(log => {
    const matchesSearch = log.action.toLowerCase().includes(search.toLowerCase()) ||
                         log.user.toLowerCase().includes(search.toLowerCase());
    const matchesCategory = selectedCategory === 'All' || log.category === selectedCategory;
    return matchesSearch && matchesCategory;
  });

  const getCategoryIcon = (category: string) => {
    switch (category) {
      case 'auth': return <Shield className="h-4 w-4" />;
      case 'user': return <User className="h-4 w-4" />;
      case 'settings': return <Settings className="h-4 w-4" />;
      case 'data': return <Database className="h-4 w-4" />;
      default: return <FileText className="h-4 w-4" />;
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Audit Logs</h1>
          <p className="text-gray-600">Track all system activities and changes</p>
        </div>
        <Button variant="outline">
          <Download className="mr-2 h-4 w-4" />
          Export Logs
        </Button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {[
          { label: 'Total Events', value: '12,456', change: '+234 today' },
          { label: 'Successful', value: '12,102', change: '97.2%' },
          { label: 'Failed', value: '354', change: '2.8%' },
          { label: 'Unique Users', value: '89', change: '+5 this week' },
        ].map((stat, i) => (
          <Card key={i} className="p-4">
            <p className="text-sm text-gray-500">{stat.label}</p>
            <p className="text-2xl font-bold text-gray-900">{stat.value}</p>
            <p className="text-xs text-gray-500 mt-1">{stat.change}</p>
          </Card>
        ))}
      </div>

      {/* Filters */}
      <Card className="p-4">
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex-1 min-w-[200px]">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
              <input
                type="text"
                placeholder="Search logs..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-md"
              />
            </div>
          </div>
          <div className="flex gap-2">
            {categories.map((cat) => (
              <button
                key={cat}
                onClick={() => setSelectedCategory(cat)}
                className={cn(
                  'px-3 py-1 rounded-full text-sm capitalize',
                  selectedCategory === cat
                    ? 'bg-blue-100 text-blue-700'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                )}
              >
                {cat}
              </button>
            ))}
          </div>
        </div>
      </Card>

      {/* Logs Table */}
      <Card>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">Action</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">User</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">Category</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">IP Address</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">Timestamp</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {filteredLogs.map((log) => (
                <tr key={log.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4">
                    <div className="flex items-center">
                      <div className="p-1 bg-gray-100 rounded mr-3">
                        {getCategoryIcon(log.category)}
                      </div>
                      <span className="font-medium text-gray-900">{log.action}</span>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-gray-600">{log.user}</td>
                  <td className="px-6 py-4">
                    <Badge variant="secondary" className="capitalize">{log.category}</Badge>
                  </td>
                  <td className="px-6 py-4 text-gray-600 font-mono text-sm">{log.ip}</td>
                  <td className="px-6 py-4 text-gray-600 text-sm">{log.timestamp}</td>
                  <td className="px-6 py-4">
                    <Badge className={log.status === 'success' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}>
                      {log.status}
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        <div className="flex items-center justify-between px-6 py-4 border-t">
          <p className="text-sm text-gray-500">Showing 1 to {filteredLogs.length} of {filteredLogs.length} entries</p>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" disabled>
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span className="text-sm">Page {page}</span>
            <Button variant="outline" size="sm" disabled>
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
};

export default AuditLogs;
