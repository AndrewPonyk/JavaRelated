import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Layout, Search, Download, BarChart3, PieChart, Table, Type, Map, Activity, Loader2, RefreshCw, LineChart, Hash, Grid, Gauge } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { useToastHelpers } from '@/components/ui/Toaster';
import { queryKeys } from '@/services/cache/queryClient';
import { widgetApi, WidgetTemplate, WidgetFilters } from '@/services/api/widgetApi';
import { cn } from '@/utils/cn';

const templateCategories = ['All', 'Charts', 'Metrics', 'Tables', 'Maps', 'Text'];

const typeToCategory: Record<string, string> = {
  'CHART_LINE': 'Charts',
  'CHART_BAR': 'Charts',
  'CHART_PIE': 'Charts',
  'CHART_AREA': 'Charts',
  'CHART_SCATTER': 'Charts',
  'TABLE': 'Tables',
  'METRIC': 'Metrics',
  'GAUGE': 'Metrics',
  'TEXT': 'Text',
  'MAP': 'Maps',
  'HEATMAP': 'Maps',
  'IMAGE': 'Text',
  'CUSTOM': 'Text'
};

const typeIcons: Record<string, React.ElementType> = {
  'CHART_LINE': LineChart,
  'CHART_BAR': BarChart3,
  'CHART_PIE': PieChart,
  'CHART_AREA': Activity,
  'TABLE': Table,
  'METRIC': Hash,
  'GAUGE': Gauge,
  'TEXT': Type,
  'MAP': Map,
  'HEATMAP': Grid,
  'IMAGE': Layout,
  'CUSTOM': Layout,
};

const typeLabels: Record<string, string> = {
  'CHART_LINE': 'Line Chart',
  'CHART_BAR': 'Bar Chart',
  'CHART_PIE': 'Pie Chart',
  'CHART_AREA': 'Area Chart',
  'CHART_SCATTER': 'Scatter Plot',
  'TABLE': 'Data Table',
  'METRIC': 'KPI Card',
  'GAUGE': 'Gauge',
  'TEXT': 'Rich Text',
  'MAP': 'Map',
  'HEATMAP': 'Heat Map',
  'IMAGE': 'Image',
  'CUSTOM': 'Custom',
};

export const WidgetTemplates: React.FC = () => {
  const queryClient = useQueryClient();
  const { success: showSuccess, error: showError } = useToastHelpers();

  const [search, setSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('All');
  const [page, setPage] = useState(1);

  // Map category to type filter
  const getTypeFilter = () => {
    if (selectedCategory === 'All') return undefined;

    const types = Object.entries(typeToCategory)
      .filter(([, cat]) => cat === selectedCategory)
      .map(([type]) => type);

    return types[0]; // API accepts single type, or we could modify to accept array
  };

  const filters: WidgetFilters = {
    page,
    limit: 12,
    search: search || undefined,
    type: getTypeFilter(),
  };

  // Fetch templates
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.widgets.templates(filters),
    queryFn: () => widgetApi.getTemplates(filters),
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  // Use template mutation
  const useMutation_ = useMutation({
    mutationFn: (template: WidgetTemplate) => {
      // For now, show a message - in production would open a dashboard selector
      return Promise.resolve(template);
    },
    onSuccess: (template) => {
      showSuccess(`Template "${template.title}" ready to use. Select a dashboard to add it.`);
    },
    onError: () => {
      showError('Failed to use template');
    }
  });

  const handleUseTemplate = (template: WidgetTemplate) => {
    useMutation_.mutate(template);
  };

  // Filter client-side by category if needed
  const templates = data?.data || [];
  const filteredTemplates = selectedCategory === 'All'
    ? templates
    : templates.filter(t => typeToCategory[t.type] === selectedCategory);

  const meta = data?.meta;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Widget Templates</h1>
          <p className="text-gray-600">Pre-built templates to get started quickly</p>
        </div>
        <Button variant="outline" size="icon" onClick={() => refetch()}>
          <RefreshCw className={cn("h-4 w-4", isLoading && "animate-spin")} />
        </Button>
      </div>

      <Card className="p-4">
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex-1 min-w-[200px]">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
              <input
                type="text"
                placeholder="Search templates..."
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
            {templateCategories.map((cat) => (
              <button
                key={cat}
                onClick={() => {
                  setSelectedCategory(cat);
                  setPage(1);
                }}
                className={cn(
                  'px-3 py-1 rounded-full text-sm',
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

      {/* Loading state */}
      {isLoading && (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
        </div>
      )}

      {/* Error state */}
      {isError && (
        <Card className="p-8 text-center">
          <p className="text-red-500 mb-4">Failed to load templates</p>
          <Button onClick={() => refetch()}>Try Again</Button>
        </Card>
      )}

      {!isLoading && !isError && (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            {filteredTemplates.map((template) => {
              const Icon = typeIcons[template.type] || Layout;
              const category = typeToCategory[template.type] || 'Other';

              return (
                <Card key={template.id} className="p-4 hover:shadow-md transition-shadow cursor-pointer group">
                  <div className="aspect-video bg-gray-100 rounded-lg mb-4 flex items-center justify-center group-hover:bg-blue-50 transition-colors">
                    <Icon className="h-12 w-12 text-gray-400 group-hover:text-blue-500 transition-colors" />
                  </div>
                  <h3 className="font-medium text-gray-900">
                    {typeLabels[template.type] || template.title}
                  </h3>
                  <p className="text-sm text-gray-500 mt-1 line-clamp-2">
                    {template.description || `${typeLabels[template.type]} widget template`}
                  </p>
                  <div className="flex items-center justify-between mt-4">
                    <Badge variant="secondary">{category}</Badge>
                    <div className="flex items-center text-sm text-gray-500">
                      <Download className="h-4 w-4 mr-1" />
                      {template.downloads || 0}
                    </div>
                  </div>
                  <Button
                    className="w-full mt-4"
                    size="sm"
                    onClick={() => handleUseTemplate(template)}
                    disabled={useMutation_.isPending}
                  >
                    {useMutation_.isPending ? (
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    ) : null}
                    Use Template
                  </Button>
                </Card>
              );
            })}

            {filteredTemplates.length === 0 && (
              <div className="col-span-full text-center py-12 text-gray-500">
                <Layout className="h-12 w-12 mx-auto mb-4 text-gray-300" />
                <p className="font-medium">No templates found</p>
                <p className="text-sm">Try adjusting your search or category filter</p>
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

export default WidgetTemplates;
