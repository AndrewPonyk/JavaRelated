import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { LayoutGrid, Plus, Search, MoreVertical, Edit, Trash2, Copy, Eye, Loader2, RefreshCw } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/DropdownMenu';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/Dialog';
import { useToastHelpers } from '@/components/ui/Toaster';
import { queryKeys } from '@/services/cache/queryClient';
import { widgetApi, Widget, WidgetFilters } from '@/services/api/widgetApi';
import { cn } from '@/utils/cn';

const widgetTypes = ['All', 'CHART_LINE', 'CHART_BAR', 'CHART_PIE', 'TABLE', 'METRIC', 'TEXT', 'MAP'];

const typeLabels: Record<string, string> = {
  'All': 'All',
  'CHART_LINE': 'Line Chart',
  'CHART_BAR': 'Bar Chart',
  'CHART_PIE': 'Pie Chart',
  'CHART_AREA': 'Area Chart',
  'CHART_SCATTER': 'Scatter',
  'TABLE': 'Table',
  'METRIC': 'Metric',
  'TEXT': 'Text',
  'IMAGE': 'Image',
  'MAP': 'Map',
  'HEATMAP': 'Heatmap',
  'GAUGE': 'Gauge',
  'CUSTOM': 'Custom'
};

export const MyWidgets: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { success: showSuccess, error: showError } = useToastHelpers();

  const [search, setSearch] = useState('');
  const [selectedType, setSelectedType] = useState('All');
  const [page, setPage] = useState(1);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [widgetToDelete, setWidgetToDelete] = useState<Widget | null>(null);

  // Build filters
  const filters: WidgetFilters = {
    page,
    limit: 12,
    search: search || undefined,
    type: selectedType !== 'All' ? selectedType : undefined,
    sortBy: 'updatedAt',
    sortOrder: 'desc'
  };

  // Fetch widgets
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.widgets.list(filters),
    queryFn: () => widgetApi.getMyWidgets(filters),
    staleTime: 2 * 60 * 1000, // 2 minutes
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: (id: string) => widgetApi.deleteWidget(id),
    onSuccess: () => {
      showSuccess('Widget deleted successfully');
      queryClient.invalidateQueries({ queryKey: queryKeys.widgets.lists() });
      setDeleteDialogOpen(false);
      setWidgetToDelete(null);
    },
    onError: () => {
      showError('Failed to delete widget');
    }
  });

  // Duplicate mutation
  const duplicateMutation = useMutation({
    mutationFn: (id: string) => widgetApi.duplicateWidget(id),
    onSuccess: (result) => {
      showSuccess('Widget duplicated successfully');
      queryClient.invalidateQueries({ queryKey: queryKeys.widgets.lists() });
    },
    onError: () => {
      showError('Failed to duplicate widget');
    }
  });

  const handleDelete = (widget: Widget) => {
    setWidgetToDelete(widget);
    setDeleteDialogOpen(true);
  };

  const confirmDelete = () => {
    if (widgetToDelete) {
      deleteMutation.mutate(widgetToDelete.id);
    }
  };

  const handleDuplicate = (widget: Widget) => {
    duplicateMutation.mutate(widget.id);
  };

  const handlePreview = (widget: Widget) => {
    navigate(`/dashboard/${widget.dashboardId}?widget=${widget.id}`);
  };

  const handleEdit = (widget: Widget) => {
    navigate(`/dashboard/${widget.dashboardId}?edit=${widget.id}`);
  };

  const handleCreateWidget = () => {
    // Navigate to dashboard builder or show create dialog
    showSuccess('Widget creation coming soon');
  };

  const getTypeColor = (type: string) => {
    const colors: Record<string, string> = {
      CHART_LINE: 'bg-blue-100 text-blue-800',
      CHART_BAR: 'bg-blue-100 text-blue-800',
      CHART_PIE: 'bg-purple-100 text-purple-800',
      CHART_AREA: 'bg-cyan-100 text-cyan-800',
      CHART_SCATTER: 'bg-teal-100 text-teal-800',
      TABLE: 'bg-green-100 text-green-800',
      METRIC: 'bg-orange-100 text-orange-800',
      TEXT: 'bg-gray-100 text-gray-800',
      IMAGE: 'bg-pink-100 text-pink-800',
      MAP: 'bg-yellow-100 text-yellow-800',
      HEATMAP: 'bg-red-100 text-red-800',
      GAUGE: 'bg-indigo-100 text-indigo-800',
      CUSTOM: 'bg-gray-100 text-gray-800',
    };
    return colors[type] || 'bg-gray-100 text-gray-800';
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  };

  const widgets = data?.data || [];
  const meta = data?.meta;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">My Widgets</h1>
          <p className="text-gray-600">Manage your custom widgets</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="icon" onClick={() => refetch()}>
            <RefreshCw className={cn("h-4 w-4", isLoading && "animate-spin")} />
          </Button>
          <Button onClick={handleCreateWidget}>
            <Plus className="mr-2 h-4 w-4" />
            Create Widget
          </Button>
        </div>
      </div>

      {/* Filters */}
      <Card className="p-4">
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex-1 min-w-[200px]">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
              <input
                type="text"
                placeholder="Search widgets..."
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(1);
                }}
                className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>
          <div className="flex gap-2 flex-wrap">
            {widgetTypes.map((type) => (
              <button
                key={type}
                onClick={() => {
                  setSelectedType(type);
                  setPage(1);
                }}
                className={cn(
                  'px-3 py-1 rounded-full text-sm whitespace-nowrap',
                  selectedType === type
                    ? 'bg-blue-100 text-blue-700'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                )}
              >
                {typeLabels[type] || type}
              </button>
            ))}
          </div>
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
          <p className="text-red-500 mb-4">Failed to load widgets</p>
          <Button onClick={() => refetch()}>Try Again</Button>
        </Card>
      )}

      {/* Widgets Grid */}
      {!isLoading && !isError && (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {widgets.map((widget) => (
              <Card key={widget.id} className="p-4 hover:shadow-md transition-shadow">
                <div className="flex items-start justify-between">
                  <div className="flex items-center space-x-3">
                    <div className="p-2 bg-gray-100 rounded-lg">
                      <LayoutGrid className="h-5 w-5 text-gray-600" />
                    </div>
                    <div>
                      <h3 className="font-medium text-gray-900">{widget.title}</h3>
                      <Badge className={cn('mt-1', getTypeColor(widget.type))}>
                        {typeLabels[widget.type] || widget.type}
                      </Badge>
                    </div>
                  </div>
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="icon">
                        <MoreVertical className="h-4 w-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onClick={() => handlePreview(widget)}>
                        <Eye className="mr-2 h-4 w-4" />
                        Preview
                      </DropdownMenuItem>
                      <DropdownMenuItem onClick={() => handleEdit(widget)}>
                        <Edit className="mr-2 h-4 w-4" />
                        Edit
                      </DropdownMenuItem>
                      <DropdownMenuItem onClick={() => handleDuplicate(widget)}>
                        <Copy className="mr-2 h-4 w-4" />
                        Duplicate
                      </DropdownMenuItem>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem
                        className="text-red-600"
                        onClick={() => handleDelete(widget)}
                      >
                        <Trash2 className="mr-2 h-4 w-4" />
                        Delete
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>

                <p className="mt-3 text-sm text-gray-600 line-clamp-2">
                  {widget.description || 'No description'}
                </p>

                {widget.dashboard && (
                  <div className="mt-3 text-xs text-gray-500">
                    Dashboard: {widget.dashboard.title}
                  </div>
                )}

                <div className="mt-4 pt-4 border-t flex items-center justify-between text-sm text-gray-500">
                  <span>Created {formatDate(widget.createdAt)}</span>
                </div>
              </Card>
            ))}

            {widgets.length === 0 && (
              <div className="col-span-full text-center py-12 text-gray-500">
                <LayoutGrid className="h-12 w-12 mx-auto mb-4 text-gray-300" />
                <p className="font-medium">No widgets found</p>
                <p className="text-sm">Try adjusting your search or filters</p>
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

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Widget</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete "{widgetToDelete?.title}"? This action cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={confirmDelete}
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : null}
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default MyWidgets;
