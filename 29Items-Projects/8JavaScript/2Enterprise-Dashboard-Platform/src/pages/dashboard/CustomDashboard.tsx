import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Edit, Eye, Share, Settings, MoreVertical, ArrowLeft, Trash2,
  Download, Copy, FileImage, FileText, Loader2, X
} from 'lucide-react';
import { Dashboard, WidgetData } from '@/types/dashboard';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
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
import DashboardBuilder from '@/components/dashboard/DashboardBuilder';
import { useToastHelpers } from '@/components/ui/Toaster';
import { useAuth } from '@/components/auth/AuthProvider';
import { dashboardApi, ExportJob } from '@/services/api/dashboardApi';
import { queryKeys } from '@/services/cache/queryClient';
import { cn } from '@/utils/cn';

// Types for dialogs
interface ShareDialogState {
  open: boolean;
  email: string;
  permission: 'READ' | 'WRITE' | 'ADMIN';
}

interface SettingsDialogState {
  open: boolean;
  title: string;
  description: string;
  isPublic: boolean;
  autoRefresh: boolean;
  refreshInterval: number;
}

export const CustomDashboard: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const { success: showSuccess, error: showError } = useToastHelpers();

  const [isEditing, setIsEditing] = useState(id === 'new');
  const [widgetData, setWidgetData] = useState<Record<string, WidgetData>>({});

  // Dialog states
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [shareDialog, setShareDialog] = useState<ShareDialogState>({
    open: false,
    email: '',
    permission: 'READ',
  });
  const [settingsDialog, setSettingsDialog] = useState<SettingsDialogState>({
    open: false,
    title: '',
    description: '',
    isPublic: false,
    autoRefresh: true,
    refreshInterval: 30,
  });
  const [exportStatus, setExportStatus] = useState<ExportJob | null>(null);

  // Fetch dashboard data
  const { data: dashboardResponse, isLoading, isError } = useQuery({
    queryKey: queryKeys.dashboards.detail(id || ''),
    queryFn: () => dashboardApi.getDashboard(id || ''),
    enabled: !!id && id !== 'new',
    staleTime: 2 * 60 * 1000,
  });

  const dashboard = dashboardResponse?.data;

  // Initialize widget data when dashboard loads
  useEffect(() => {
    if (dashboard?.widgets) {
      const initialWidgetData: Record<string, WidgetData> = {};
      dashboard.widgets.forEach((widget) => {
        initialWidgetData[widget.id] = {
          id: widget.id,
          data: widget.data,
          timestamp: new Date().toISOString(),
          loading: false,
        };
      });
      setWidgetData(initialWidgetData);
    }
  }, [dashboard]);

  // Initialize settings dialog with dashboard data
  useEffect(() => {
    if (dashboard) {
      setSettingsDialog((prev) => ({
        ...prev,
        title: dashboard.title || '',
        description: dashboard.description || '',
        isPublic: dashboard.isPublic || false,
        autoRefresh: dashboard.settings?.autoRefresh ?? true,
        refreshInterval: dashboard.settings?.refreshInterval ?? 30,
      }));
    }
  }, [dashboard]);

  // Update dashboard mutation
  const updateMutation = useMutation({
    mutationFn: (data: Partial<Dashboard>) =>
      dashboardApi.updateDashboard(id || '', data),
    onSuccess: () => {
      showSuccess('Dashboard updated');
      queryClient.invalidateQueries({ queryKey: queryKeys.dashboards.detail(id || '') });
    },
    onError: () => {
      showError('Failed to update dashboard');
    },
  });

  // Delete dashboard mutation
  const deleteMutation = useMutation({
    mutationFn: () => dashboardApi.deleteDashboard(id || ''),
    onSuccess: () => {
      showSuccess('Dashboard deleted');
      queryClient.invalidateQueries({ queryKey: queryKeys.dashboards.lists() });
      navigate('/dashboard');
    },
    onError: () => {
      showError('Failed to delete dashboard');
    },
  });

  // Duplicate dashboard mutation
  const duplicateMutation = useMutation({
    mutationFn: () => dashboardApi.duplicateDashboard(id || '', `${dashboard?.title} (Copy)`),
    onSuccess: (result) => {
      showSuccess('Dashboard duplicated');
      queryClient.invalidateQueries({ queryKey: queryKeys.dashboards.lists() });
      if (result.data?.id) {
        navigate(`/dashboard/${result.data.id}`);
      }
    },
    onError: () => {
      showError('Failed to duplicate dashboard');
    },
  });

  // Export dashboard mutation
  const exportMutation = useMutation({
    mutationFn: (format: 'pdf' | 'png') =>
      dashboardApi.exportDashboard(id || '', format, { includeData: true }),
    onSuccess: (result) => {
      setExportStatus(result.data);
      pollExportStatus(result.data.id);
    },
    onError: () => {
      showError('Failed to start export');
    },
  });

  // Poll export status
  const pollExportStatus = useCallback(async (exportId: string) => {
    const checkStatus = async () => {
      try {
        const result = await dashboardApi.getExportStatus(id || '', exportId);
        setExportStatus(result.data);

        if (result.data.status === 'completed' && result.data.downloadUrl) {
          showSuccess('Export ready');
          // Trigger download
          window.open(result.data.downloadUrl, '_blank');
          setExportStatus(null);
        } else if (result.data.status === 'failed') {
          showError('Export failed: ' + (result.data.error || 'Unknown error'));
          setExportStatus(null);
        } else if (result.data.status === 'pending' || result.data.status === 'processing') {
          // Continue polling
          setTimeout(checkStatus, 2000);
        }
      } catch {
        showError('Failed to check export status');
        setExportStatus(null);
      }
    };

    checkStatus();
  }, [id, showSuccess, showError]);

  // Share dashboard mutation
  const shareMutation = useMutation({
    mutationFn: ({ email, permission }: { email: string; permission: 'READ' | 'WRITE' | 'ADMIN' }) =>
      dashboardApi.shareDashboard(id || '', [{ userId: email, permission }]),
    onSuccess: () => {
      showSuccess('Dashboard shared successfully');
      setShareDialog({ open: false, email: '', permission: 'READ' });
      queryClient.invalidateQueries({ queryKey: queryKeys.dashboards.shares(id || '') });
    },
    onError: () => {
      showError('Failed to share dashboard');
    },
  });

  const handleDashboardChange = (updatedDashboard: Dashboard) => {
    // Optimistically update local state
    queryClient.setQueryData(queryKeys.dashboards.detail(id || ''), {
      success: true,
      data: updatedDashboard,
    });
  };

  const handleSaveDashboard = async (dashboardToSave: Dashboard) => {
    if (id === 'new') {
      try {
        const result = await dashboardApi.createDashboard(dashboardToSave);
        if (result.data?.id) {
          showSuccess('Dashboard created');
          queryClient.invalidateQueries({ queryKey: queryKeys.dashboards.lists() });
          navigate(`/dashboard/${result.data.id}`, { replace: true });
        }
      } catch {
        showError('Failed to create dashboard');
      }
    } else {
      updateMutation.mutate(dashboardToSave);
    }
  };

  const handleRefreshWidget = async (widgetId: string) => {
    setWidgetData((prev) => ({
      ...prev,
      [widgetId]: { ...prev[widgetId], loading: true },
    }));

    try {
      // In a real app, this would fetch fresh data from the widget's data source
      await new Promise((resolve) => setTimeout(resolve, 1000));

      const widget = dashboard?.widgets.find((w) => w.id === widgetId);
      if (widget) {
        setWidgetData((prev) => ({
          ...prev,
          [widgetId]: {
            ...prev[widgetId],
            data: widget.data,
            timestamp: new Date().toISOString(),
            loading: false,
          },
        }));
      }

      showSuccess('Widget refreshed');
    } catch {
      setWidgetData((prev) => ({
        ...prev,
        [widgetId]: {
          ...prev[widgetId],
          loading: false,
          error: 'Failed to refresh data',
        },
      }));
      showError('Failed to refresh widget');
    }
  };

  const handleShare = () => {
    setShareDialog({ open: true, email: '', permission: 'READ' });
  };

  const handleShareSubmit = () => {
    if (!shareDialog.email) {
      showError('Please enter an email address');
      return;
    }
    shareMutation.mutate({ email: shareDialog.email, permission: shareDialog.permission });
  };

  const handleCopyLink = async () => {
    const url = `${window.location.origin}/dashboard/${id}`;
    try {
      await navigator.clipboard.writeText(url);
      showSuccess('Link copied to clipboard');
    } catch {
      showError('Failed to copy link');
    }
  };

  const handleExportPDF = () => {
    exportMutation.mutate('pdf');
  };

  const handleExportImage = () => {
    exportMutation.mutate('png');
  };

  const handleOpenSettings = () => {
    setSettingsDialog((prev) => ({ ...prev, open: true }));
  };

  const handleSaveSettings = () => {
    updateMutation.mutate({
      title: settingsDialog.title,
      description: settingsDialog.description,
      isPublic: settingsDialog.isPublic,
      settings: {
        ...dashboard?.settings,
        autoRefresh: settingsDialog.autoRefresh,
        refreshInterval: settingsDialog.refreshInterval,
      },
    });
    setSettingsDialog((prev) => ({ ...prev, open: false }));
  };

  const handleDelete = () => {
    setDeleteDialogOpen(true);
  };

  const confirmDelete = () => {
    deleteMutation.mutate();
  };

  const handleDuplicate = () => {
    duplicateMutation.mutate();
  };

  // Handle new dashboard creation
  if (id === 'new') {
    const newDashboard: Dashboard = {
      id: 'new',
      title: 'New Dashboard',
      description: '',
      widgets: [],
      layout: {
        cols: 12,
        rowHeight: 60,
        margin: [16, 16],
        padding: [0, 0],
        breakpoints: { lg: 1200, md: 996, sm: 768, xs: 480, xxs: 0 },
        layouts: {},
      },
      settings: {
        theme: 'light',
        autoRefresh: true,
        refreshInterval: 30,
        showTitle: true,
        showDescription: true,
        allowEditing: true,
        allowSharing: true,
        snapToGrid: true,
        compactType: 'vertical',
      },
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      createdBy: user?.id || '',
      isPublic: false,
      tags: [],
    };

    return (
      <div className="h-screen flex flex-col">
        <div className="bg-white border-b border-gray-200 px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-4">
              <Button variant="ghost" size="sm" onClick={() => navigate('/dashboard')}>
                <ArrowLeft className="h-4 w-4" />
              </Button>
              <div>
                <h1 className="text-xl font-semibold text-gray-900">New Dashboard</h1>
                <p className="text-sm text-gray-600">Create a new dashboard</p>
              </div>
            </div>
          </div>
        </div>
        <div className="flex-1 overflow-hidden">
          <DashboardBuilder
            dashboard={newDashboard}
            isEditing={true}
            widgetData={{}}
            onDashboardChange={() => {}}
            onSaveDashboard={handleSaveDashboard}
            onWidgetDataRefresh={() => {}}
          />
        </div>
      </div>
    );
  }

  if (!id) {
    return (
      <div className="h-screen flex items-center justify-center">
        <Card className="p-8 text-center">
          <h2 className="text-lg font-medium text-gray-900 mb-2">No dashboard selected</h2>
          <Button onClick={() => navigate('/dashboard')}>
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to Dashboard
          </Button>
        </Card>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="h-screen flex items-center justify-center">
        <div className="flex items-center space-x-2 text-gray-600">
          <Loader2 className="h-8 w-8 animate-spin text-blue-600" />
          <span className="font-medium">Loading dashboard...</span>
        </div>
      </div>
    );
  }

  if (isError || !dashboard) {
    return (
      <div className="h-screen flex items-center justify-center">
        <Card className="p-8 text-center">
          <h2 className="text-lg font-medium text-gray-900 mb-2">Dashboard not found</h2>
          <p className="text-gray-600 mb-4">The dashboard you're looking for doesn't exist.</p>
          <Button onClick={() => navigate('/dashboard')}>
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to Dashboard
          </Button>
        </Card>
      </div>
    );
  }

  return (
    <div className="h-screen flex flex-col">
      {/* Header */}
      <div className="bg-white border-b border-gray-200 px-6 py-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-4">
            <Button variant="ghost" size="sm" onClick={() => navigate('/dashboard')}>
              <ArrowLeft className="h-4 w-4" />
            </Button>

            <div>
              <h1 className="text-xl font-semibold text-gray-900">{dashboard.title}</h1>
              {dashboard.description && (
                <p className="text-sm text-gray-600">{dashboard.description}</p>
              )}
            </div>

            {dashboard.tags && dashboard.tags.length > 0 && (
              <div className="flex items-center space-x-1">
                {dashboard.tags.map((tag) => (
                  <span
                    key={tag}
                    className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-gray-100 text-gray-800"
                  >
                    {tag}
                  </span>
                ))}
              </div>
            )}
          </div>

          <div className="flex items-center space-x-2">
            {!isEditing ? (
              <>
                <Button variant="outline" size="sm" onClick={() => setIsEditing(true)}>
                  <Edit className="mr-2 h-4 w-4" />
                  Edit
                </Button>
                <Button variant="outline" size="sm" onClick={handleShare}>
                  <Share className="mr-2 h-4 w-4" />
                  Share
                </Button>
              </>
            ) : (
              <Button variant="outline" size="sm" onClick={() => setIsEditing(false)}>
                <Eye className="mr-2 h-4 w-4" />
                Preview
              </Button>
            )}

            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon">
                  <MoreVertical className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={handleOpenSettings}>
                  <Settings className="mr-2 h-4 w-4" />
                  Dashboard Settings
                </DropdownMenuItem>
                <DropdownMenuItem onClick={handleCopyLink}>
                  <Copy className="mr-2 h-4 w-4" />
                  Copy Link
                </DropdownMenuItem>
                <DropdownMenuItem onClick={handleDuplicate} disabled={duplicateMutation.isPending}>
                  <Copy className="mr-2 h-4 w-4" />
                  Duplicate Dashboard
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={handleExportPDF} disabled={exportMutation.isPending}>
                  <FileText className="mr-2 h-4 w-4" />
                  Export as PDF
                </DropdownMenuItem>
                <DropdownMenuItem onClick={handleExportImage} disabled={exportMutation.isPending}>
                  <FileImage className="mr-2 h-4 w-4" />
                  Export as Image
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  className="text-red-600 focus:text-red-600"
                  onClick={handleDelete}
                >
                  <Trash2 className="mr-2 h-4 w-4" />
                  Delete Dashboard
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </div>

      {/* Export Progress Indicator */}
      {exportStatus && (
        <div className="bg-blue-50 border-b border-blue-200 px-6 py-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-3">
              <Loader2 className="h-4 w-4 animate-spin text-blue-600" />
              <span className="text-sm text-blue-700">
                Exporting dashboard... {exportStatus.progress}%
              </span>
            </div>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setExportStatus(null)}
            >
              <X className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      {/* Dashboard Content */}
      <div className="flex-1 overflow-hidden">
        <DashboardBuilder
          dashboard={dashboard}
          isEditing={isEditing}
          widgetData={widgetData}
          onDashboardChange={handleDashboardChange}
          onSaveDashboard={handleSaveDashboard}
          onWidgetDataRefresh={handleRefreshWidget}
        />
      </div>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Dashboard</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete "{dashboard.title}"? This action cannot be undone.
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

      {/* Share Dialog */}
      <Dialog open={shareDialog.open} onOpenChange={(open) => setShareDialog((prev) => ({ ...prev, open }))}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Share Dashboard</DialogTitle>
            <DialogDescription>
              Share this dashboard with other users by entering their email address.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="share-email">Email Address</Label>
              <Input
                id="share-email"
                type="email"
                placeholder="user@example.com"
                value={shareDialog.email}
                onChange={(e) => setShareDialog((prev) => ({ ...prev, email: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label>Permission Level</Label>
              <div className="flex gap-2">
                {(['READ', 'WRITE', 'ADMIN'] as const).map((perm) => (
                  <Button
                    key={perm}
                    variant={shareDialog.permission === perm ? 'default' : 'outline'}
                    size="sm"
                    onClick={() => setShareDialog((prev) => ({ ...prev, permission: perm }))}
                  >
                    {perm === 'READ' ? 'View' : perm === 'WRITE' ? 'Edit' : 'Admin'}
                  </Button>
                ))}
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShareDialog((prev) => ({ ...prev, open: false }))}>
              Cancel
            </Button>
            <Button onClick={handleShareSubmit} disabled={shareMutation.isPending}>
              {shareMutation.isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <Share className="mr-2 h-4 w-4" />
              )}
              Share
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Settings Dialog */}
      <Dialog open={settingsDialog.open} onOpenChange={(open) => setSettingsDialog((prev) => ({ ...prev, open }))}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Dashboard Settings</DialogTitle>
            <DialogDescription>Configure your dashboard settings.</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="settings-title">Title</Label>
              <Input
                id="settings-title"
                value={settingsDialog.title}
                onChange={(e) => setSettingsDialog((prev) => ({ ...prev, title: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="settings-description">Description</Label>
              <textarea
                id="settings-description"
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                rows={3}
                value={settingsDialog.description}
                onChange={(e) => setSettingsDialog((prev) => ({ ...prev, description: e.target.value }))}
              />
            </div>
            <div className="flex items-center justify-between">
              <div>
                <Label>Public Dashboard</Label>
                <p className="text-xs text-gray-500">Anyone with the link can view</p>
              </div>
              <button
                onClick={() => setSettingsDialog((prev) => ({ ...prev, isPublic: !prev.isPublic }))}
                className={cn(
                  'relative w-11 h-6 rounded-full transition-colors',
                  settingsDialog.isPublic ? 'bg-blue-600' : 'bg-gray-200'
                )}
              >
                <span
                  className={cn(
                    'absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform',
                    settingsDialog.isPublic && 'translate-x-5'
                  )}
                />
              </button>
            </div>
            <div className="flex items-center justify-between">
              <div>
                <Label>Auto Refresh</Label>
                <p className="text-xs text-gray-500">Automatically refresh data</p>
              </div>
              <button
                onClick={() => setSettingsDialog((prev) => ({ ...prev, autoRefresh: !prev.autoRefresh }))}
                className={cn(
                  'relative w-11 h-6 rounded-full transition-colors',
                  settingsDialog.autoRefresh ? 'bg-blue-600' : 'bg-gray-200'
                )}
              >
                <span
                  className={cn(
                    'absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform',
                    settingsDialog.autoRefresh && 'translate-x-5'
                  )}
                />
              </button>
            </div>
            {settingsDialog.autoRefresh && (
              <div className="space-y-2">
                <Label>Refresh Interval (seconds)</Label>
                <Input
                  type="number"
                  min={10}
                  max={300}
                  value={settingsDialog.refreshInterval}
                  onChange={(e) =>
                    setSettingsDialog((prev) => ({
                      ...prev,
                      refreshInterval: parseInt(e.target.value) || 30,
                    }))
                  }
                />
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setSettingsDialog((prev) => ({ ...prev, open: false }))}>
              Cancel
            </Button>
            <Button onClick={handleSaveSettings} disabled={updateMutation.isPending}>
              {updateMutation.isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : null}
              Save Settings
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default CustomDashboard;
