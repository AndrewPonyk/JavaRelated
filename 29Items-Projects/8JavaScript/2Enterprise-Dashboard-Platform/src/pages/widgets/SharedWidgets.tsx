import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Users, Search, LayoutGrid, Star, Download, Eye, Loader2, RefreshCw, Shield } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Avatar } from '@/components/ui/Avatar';
import { useToastHelpers } from '@/components/ui/Toaster';
import { queryKeys } from '@/services/cache/queryClient';
import { widgetApi, SharedWidget, WidgetFilters } from '@/services/api/widgetApi';
import { cn } from '@/utils/cn';

const typeLabels: Record<string, string> = {
  'CHART_LINE': 'Line Chart',
  'CHART_BAR': 'Bar Chart',
  'CHART_PIE': 'Pie Chart',
  'TABLE': 'Table',
  'METRIC': 'Metric',
  'TEXT': 'Text',
  'MAP': 'Map',
};

const permissionColors: Record<string, string> = {
  'READ': 'bg-gray-100 text-gray-700',
  'WRITE': 'bg-blue-100 text-blue-700',
  'ADMIN': 'bg-purple-100 text-purple-700',
};

export const SharedWidgets: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { success: showSuccess, error: showError } = useToastHelpers();

  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);

  const filters: WidgetFilters = {
    page,
    limit: 12,
    search: search || undefined,
  };

  // Fetch shared widgets
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.widgets.shared(filters),
    queryFn: () => widgetApi.getSharedWidgets(filters),
    staleTime: 2 * 60 * 1000,
  });

  // Duplicate (use) mutation
  const duplicateMutation = useMutation({
    mutationFn: (id: string) => widgetApi.duplicateWidget(id),
    onSuccess: () => {
      showSuccess('Widget copied to your collection');
      queryClient.invalidateQueries({ queryKey: queryKeys.widgets.lists() });
    },
    onError: () => {
      showError('Failed to copy widget');
    }
  });

  const handlePreview = (widget: SharedWidget) => {
    navigate(`/dashboard/${widget.dashboardId}?widget=${widget.id}`);
  };

  const handleUse = (widget: SharedWidget) => {
    duplicateMutation.mutate(widget.id);
  };

  const getInitials = (name: string) => {
    return name
      .split(' ')
      .map(n => n[0])
      .join('')
      .toUpperCase()
      .slice(0, 2);
  };

  const widgets = data?.data || [];
  const meta = data?.meta;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Shared Widgets</h1>
          <p className="text-gray-600">Widgets shared with you by your team</p>
        </div>
        <Button variant="outline" size="icon" onClick={() => refetch()}>
          <RefreshCw className={cn("h-4 w-4", isLoading && "animate-spin")} />
        </Button>
      </div>

      <Card className="p-4">
        <div className="relative max-w-md">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
          <input
            type="text"
            placeholder="Search shared widgets..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(1);
            }}
            className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
      </Card>

      {/* Loading state */}
      {isLoading && (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
        </div>
      )}

      {/* Error state */}
      {isError && (
        <Card className="p-8 text-center">
          <p className="text-red-500 mb-4">Failed to load shared widgets</p>
          <Button onClick={() => refetch()}>Try Again</Button>
        </Card>
      )}

      {!isLoading && !isError && (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {widgets.map((widget) => (
              <Card key={widget.id} className="p-4">
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center space-x-3">
                    <div className="p-2 bg-blue-50 rounded-lg">
                      <LayoutGrid className="h-5 w-5 text-blue-600" />
                    </div>
                    <div>
                      <h3 className="font-medium text-gray-900">{widget.title}</h3>
                      <Badge variant="secondary">{typeLabels[widget.type] || widget.type}</Badge>
                    </div>
                  </div>
                </div>

                <div className="flex items-center space-x-2 mb-3">
                  <Avatar className="h-6 w-6 bg-gray-200 text-xs">
                    {getInitials(widget.sharedBy)}
                  </Avatar>
                  <span className="text-sm text-gray-600">{widget.sharedBy}</span>
                </div>

                {widget.description && (
                  <p className="text-sm text-gray-500 mb-3 line-clamp-2">
                    {widget.description}
                  </p>
                )}

                <div className="flex items-center justify-between text-sm text-gray-500 mb-4">
                  <div className="flex items-center">
                    <Shield className="h-4 w-4 mr-1" />
                    <Badge className={cn('text-xs', permissionColors[widget.permission])}>
                      {widget.permission}
                    </Badge>
                  </div>
                  {widget.dashboard && (
                    <span className="text-xs truncate max-w-[120px]">
                      {widget.dashboard.title}
                    </span>
                  )}
                </div>

                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    className="flex-1"
                    onClick={() => handlePreview(widget)}
                  >
                    <Eye className="mr-1 h-3 w-3" />
                    Preview
                  </Button>
                  <Button
                    size="sm"
                    className="flex-1"
                    onClick={() => handleUse(widget)}
                    disabled={duplicateMutation.isPending}
                  >
                    {duplicateMutation.isPending ? (
                      <Loader2 className="mr-1 h-3 w-3 animate-spin" />
                    ) : (
                      <Download className="mr-1 h-3 w-3" />
                    )}
                    Use
                  </Button>
                </div>
              </Card>
            ))}

            {widgets.length === 0 && (
              <div className="col-span-full text-center py-12 text-gray-500">
                <Users className="h-12 w-12 mx-auto mb-4 text-gray-300" />
                <p className="font-medium">No shared widgets</p>
                <p className="text-sm">Widgets shared with you will appear here</p>
              </div>
            )}
          </div>

          {/* Pagination */}
          {meta && meta.pages > 1 && (
            <div className="flex items-center justify-center gap-2 mt-6">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(p => Math.max(1, p - 1))}
                disabled={page === 1}
              >
                Previous
              </Button>
              <span className="text-sm text-gray-600">
                Page {meta.page} of {meta.pages}
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(p => Math.min(meta.pages, p + 1))}
                disabled={page === meta.pages}
              >
                Next
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default SharedWidgets;
