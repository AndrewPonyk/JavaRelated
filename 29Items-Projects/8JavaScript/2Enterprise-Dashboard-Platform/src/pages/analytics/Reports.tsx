import React, { useState } from 'react';
import { FileText, Download, Calendar, Filter, Plus, MoreVertical, Eye, Trash2, RefreshCw } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/DropdownMenu';
import { useToastHelpers } from '@/components/ui/Toaster';

const mockReports = [
  { id: '1', name: 'Monthly Sales Report', type: 'Sales', status: 'completed', createdAt: '2024-01-15', size: '2.4 MB' },
  { id: '2', name: 'User Analytics Q4', type: 'Analytics', status: 'completed', createdAt: '2024-01-14', size: '1.8 MB' },
  { id: '3', name: 'Financial Summary', type: 'Finance', status: 'processing', createdAt: '2024-01-13', size: '-' },
  { id: '4', name: 'Marketing Campaign Results', type: 'Marketing', status: 'completed', createdAt: '2024-01-12', size: '3.1 MB' },
  { id: '5', name: 'Inventory Status', type: 'Operations', status: 'failed', createdAt: '2024-01-11', size: '-' },
];

const reportTypes = ['All', 'Sales', 'Analytics', 'Finance', 'Marketing', 'Operations'];

export const Reports: React.FC = () => {
  const [reports] = useState(mockReports);
  const [selectedType, setSelectedType] = useState('All');
  const [isGenerating, setIsGenerating] = useState(false);
  const { success: showSuccess } = useToastHelpers();

  const filteredReports = selectedType === 'All'
    ? reports
    : reports.filter(r => r.type === selectedType);

  const handleGenerateReport = async () => {
    setIsGenerating(true);
    await new Promise(resolve => setTimeout(resolve, 2000));
    setIsGenerating(false);
    showSuccess('Report generated', 'Your report is being processed');
  };

  const getStatusBadge = (status: string) => {
    const styles = {
      completed: 'bg-green-100 text-green-800',
      processing: 'bg-yellow-100 text-yellow-800',
      failed: 'bg-red-100 text-red-800',
    };
    return <Badge className={styles[status as keyof typeof styles] || 'bg-gray-100'}>{status}</Badge>;
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Reports</h1>
          <p className="text-gray-600">Generate and manage your analytics reports</p>
        </div>
        <Button onClick={handleGenerateReport} disabled={isGenerating}>
          {isGenerating ? (
            <>
              <RefreshCw className="mr-2 h-4 w-4 animate-spin" />
              Generating...
            </>
          ) : (
            <>
              <Plus className="mr-2 h-4 w-4" />
              Generate Report
            </>
          )}
        </Button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {[
          { label: 'Total Reports', value: reports.length, icon: FileText },
          { label: 'Completed', value: reports.filter(r => r.status === 'completed').length, icon: FileText },
          { label: 'Processing', value: reports.filter(r => r.status === 'processing').length, icon: RefreshCw },
          { label: 'This Month', value: reports.length, icon: Calendar },
        ].map((stat, i) => (
          <Card key={i} className="p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-500">{stat.label}</p>
                <p className="text-2xl font-bold text-gray-900">{stat.value}</p>
              </div>
              <stat.icon className="h-8 w-8 text-gray-400" />
            </div>
          </Card>
        ))}
      </div>

      {/* Filters */}
      <Card className="p-4">
        <div className="flex items-center gap-4">
          <Filter className="h-5 w-5 text-gray-400" />
          <div className="flex gap-2">
            {reportTypes.map((type) => (
              <button
                key={type}
                onClick={() => setSelectedType(type)}
                className={`px-3 py-1 rounded-full text-sm ${
                  selectedType === type
                    ? 'bg-blue-100 text-blue-700'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >
                {type}
              </button>
            ))}
          </div>
        </div>
      </Card>

      {/* Reports Table */}
      <Card>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">Report Name</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">Type</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">Status</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">Created</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">Size</th>
                <th className="text-right px-6 py-3 text-xs font-medium text-gray-500 uppercase">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {filteredReports.map((report) => (
                <tr key={report.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4">
                    <div className="flex items-center">
                      <FileText className="h-5 w-5 text-gray-400 mr-3" />
                      <span className="font-medium text-gray-900">{report.name}</span>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-gray-500">{report.type}</td>
                  <td className="px-6 py-4">{getStatusBadge(report.status)}</td>
                  <td className="px-6 py-4 text-gray-500">{report.createdAt}</td>
                  <td className="px-6 py-4 text-gray-500">{report.size}</td>
                  <td className="px-6 py-4 text-right">
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon">
                          <MoreVertical className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem>
                          <Eye className="mr-2 h-4 w-4" />
                          View
                        </DropdownMenuItem>
                        <DropdownMenuItem>
                          <Download className="mr-2 h-4 w-4" />
                          Download
                        </DropdownMenuItem>
                        <DropdownMenuItem className="text-red-600">
                          <Trash2 className="mr-2 h-4 w-4" />
                          Delete
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};

export default Reports;
