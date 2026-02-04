import React, { useState, useCallback, useMemo } from 'react';
import { Responsive, WidthProvider, Layout } from 'react-grid-layout';
import { Plus, Settings, Eye, Save, Undo, Redo, Database } from 'lucide-react';
import { Dashboard, Widget, WidgetData } from '@/types/dashboard';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { getWidgetComponent, widgetTemplates } from './WidgetRegistry';
import WidgetGallery from './WidgetGallery';
import WidgetDataConfig, { DataBindingConfig } from './WidgetDataConfig';
import ShareWidgetDialog from './ShareWidgetDialog';
import FullscreenWidget from './FullscreenWidget';
import { useToastHelpers } from '@/components/ui/Toaster';
import { cn } from '@/utils/cn';

// CSS import for react-grid-layout
import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';

const ResponsiveGridLayout = WidthProvider(Responsive);

interface DashboardBuilderProps {
  dashboard: Dashboard;
  isEditing?: boolean;
  widgetData?: Record<string, WidgetData>;
  onDashboardChange?: (dashboard: Dashboard) => void;
  onSaveDashboard?: (dashboard: Dashboard) => void;
  onWidgetDataRefresh?: (widgetId: string) => void;
  className?: string;
}

export const DashboardBuilder: React.FC<DashboardBuilderProps> = ({
  dashboard,
  isEditing = false,
  widgetData = {},
  onDashboardChange,
  onSaveDashboard,
  onWidgetDataRefresh,
  className,
}) => {
  const [selectedWidgetId, setSelectedWidgetId] = useState<string | null>(null);
  const [isGalleryOpen, setIsGalleryOpen] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const [history, setHistory] = useState<Dashboard[]>([dashboard]);
  const [historyIndex, setHistoryIndex] = useState(0);
  const [dataConfigWidgetId, setDataConfigWidgetId] = useState<string | null>(null);
  const [shareWidgetId, setShareWidgetId] = useState<string | null>(null);
  const [fullscreenWidgetId, setFullscreenWidgetId] = useState<string | null>(null);

  const { success: showSuccess, error: showError } = useToastHelpers();

  const currentDashboard = history[historyIndex] || dashboard;

  const addToHistory = useCallback((newDashboard: Dashboard) => {
    const newHistory = history.slice(0, historyIndex + 1);
    newHistory.push(newDashboard);

    // Limit history to 50 items
    if (newHistory.length > 50) {
      newHistory.shift();
    } else {
      setHistoryIndex(prev => prev + 1);
    }

    setHistory(newHistory);
  }, [history, historyIndex]);

  const updateDashboard = useCallback((updates: Partial<Dashboard>) => {
    const updatedDashboard = { ...currentDashboard, ...updates };
    addToHistory(updatedDashboard);
    onDashboardChange?.(updatedDashboard);
  }, [currentDashboard, addToHistory, onDashboardChange]);

  const undo = useCallback(() => {
    if (historyIndex > 0) {
      setHistoryIndex(prev => prev - 1);
      const prevDashboard = history[historyIndex - 1];
      onDashboardChange?.(prevDashboard);
    }
  }, [history, historyIndex, onDashboardChange]);

  const redo = useCallback(() => {
    if (historyIndex < history.length - 1) {
      setHistoryIndex(prev => prev + 1);
      const nextDashboard = history[historyIndex + 1];
      onDashboardChange?.(nextDashboard);
    }
  }, [history, historyIndex, onDashboardChange]);

  // Convert widgets to grid layout format
  const layouts = useMemo(() => {
    const layoutMap: { [key: string]: Layout[] } = {};

    Object.keys(currentDashboard.layout.breakpoints).forEach(breakpoint => {
      layoutMap[breakpoint] = currentDashboard.widgets.map(widget => ({
        i: widget.id,
        x: widget.layout.x,
        y: widget.layout.y,
        w: widget.layout.w,
        h: widget.layout.h,
        minW: widget.layout.minW || 1,
        minH: widget.layout.minH || 1,
        maxW: widget.layout.maxW,
        maxH: widget.layout.maxH,
        static: !isEditing || widget.layout.static,
        isDraggable: isEditing && (widget.layout.draggable !== false),
        isResizable: isEditing && (widget.layout.resizable !== false),
      }));
    });

    return layoutMap;
  }, [currentDashboard.widgets, isEditing]);

  const handleLayoutChange = useCallback((layout: Layout[], layouts: { [key: string]: Layout[] }) => {
    if (!isEditing) return;

    const updatedWidgets = currentDashboard.widgets.map(widget => {
      const layoutItem = layout.find(item => item.i === widget.id);
      if (layoutItem) {
        return {
          ...widget,
          layout: {
            ...widget.layout,
            x: layoutItem.x,
            y: layoutItem.y,
            w: layoutItem.w,
            h: layoutItem.h,
          },
        };
      }
      return widget;
    });

    updateDashboard({
      widgets: updatedWidgets,
      layout: {
        ...currentDashboard.layout,
        layouts,
      },
    });
  }, [isEditing, currentDashboard, updateDashboard]);

  const handleAddWidget = useCallback((templateId: string) => {
    const template = widgetTemplates.find(t => t.id === templateId);
    if (!template) return;

    // Find a good position for the new widget
    const existingLayouts = layouts.lg || [];
    let maxY = 0;
    let maxX = 0;

    existingLayouts.forEach(layout => {
      maxY = Math.max(maxY, layout.y + layout.h);
      maxX = Math.max(maxX, layout.x);
    });

    const newWidget: Widget = {
      id: `widget_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      type: template.type,
      title: template.name,
      description: template.description,
      config: { ...template.defaultConfig },
      layout: {
        ...template.defaultLayout,
        x: 0,
        y: maxY,
      },
      data: template.sampleData,
    };

    updateDashboard({
      widgets: [...currentDashboard.widgets, newWidget],
    });

    setIsGalleryOpen(false);
    setSelectedWidgetId(newWidget.id);
    showSuccess('Widget added', `${template.name} has been added to your dashboard`);
  }, [layouts, currentDashboard, updateDashboard, showSuccess]);

  const handleUpdateWidget = useCallback((updatedWidget: Widget) => {
    const updatedWidgets = currentDashboard.widgets.map(widget =>
      widget.id === updatedWidget.id ? updatedWidget : widget
    );

    updateDashboard({ widgets: updatedWidgets });
  }, [currentDashboard, updateDashboard]);

  const handleDeleteWidget = useCallback((widgetId: string) => {
    const updatedWidgets = currentDashboard.widgets.filter(widget => widget.id !== widgetId);
    updateDashboard({ widgets: updatedWidgets });

    if (selectedWidgetId === widgetId) {
      setSelectedWidgetId(null);
    }

    showSuccess('Widget deleted', 'The widget has been removed from your dashboard');
  }, [currentDashboard, updateDashboard, selectedWidgetId, showSuccess]);

  const handleDuplicateWidget = useCallback((widget: Widget) => {
    const duplicatedWidget: Widget = {
      ...widget,
      id: `widget_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      title: `${widget.title} (Copy)`,
      layout: {
        ...widget.layout,
        x: widget.layout.x + 1,
        y: widget.layout.y + 1,
      },
    };

    updateDashboard({
      widgets: [...currentDashboard.widgets, duplicatedWidget],
    });

    showSuccess('Widget duplicated', 'The widget has been duplicated');
  }, [currentDashboard, updateDashboard, showSuccess]);

  const handleConfigureData = useCallback((widgetId: string) => {
    setDataConfigWidgetId(widgetId);
  }, []);

  const handleSaveDataConfig = useCallback((config: DataBindingConfig) => {
    if (!dataConfigWidgetId) return;

    const updatedWidgets = currentDashboard.widgets.map(widget => {
      if (widget.id === dataConfigWidgetId) {
        return {
          ...widget,
          config: {
            ...widget.config,
            dataBinding: config,
          },
        };
      }
      return widget;
    });

    updateDashboard({ widgets: updatedWidgets });
    setDataConfigWidgetId(null);
    showSuccess('Data configured', 'Widget data binding has been saved');
  }, [dataConfigWidgetId, currentDashboard, updateDashboard, showSuccess]);

  const handleSave = useCallback(() => {
    onSaveDashboard?.(currentDashboard);
    showSuccess('Dashboard saved', 'Your changes have been saved successfully');
  }, [currentDashboard, onSaveDashboard, showSuccess]);

  const renderWidget = useCallback((widget: Widget) => {
    const WidgetComponent = getWidgetComponent(widget.type);
    const data = widgetData[widget.id];

    return (
      <div
        key={widget.id}
        className={cn(
          'widget-container',
          selectedWidgetId === widget.id && 'selected',
          isDragging && 'dragging'
        )}
        onClick={() => isEditing && setSelectedWidgetId(widget.id)}
      >
        <WidgetComponent
          widget={widget}
          data={data}
          isEditing={isEditing}
          isSelected={selectedWidgetId === widget.id}
          isDragging={isDragging}
          onUpdate={handleUpdateWidget}
          onDelete={handleDeleteWidget}
          onDuplicate={handleDuplicateWidget}
          onRefresh={onWidgetDataRefresh}
          onSettings={(w: Widget) => {
            setSelectedWidgetId(w.id);
          }}
          onConfigureData={(w: Widget) => {
            handleConfigureData(w.id);
          }}
          onShare={(w: Widget) => {
            setShareWidgetId(w.id);
          }}
          onFullscreen={(w: Widget) => {
            setFullscreenWidgetId(w.id);
          }}
        />
      </div>
    );
  }, [
    widgetData,
    selectedWidgetId,
    isDragging,
    isEditing,
    handleUpdateWidget,
    handleDeleteWidget,
    handleDuplicateWidget,
    handleConfigureData,
    onWidgetDataRefresh,
  ]);

  // Get the widget being configured for data
  const dataConfigWidget = dataConfigWidgetId
    ? currentDashboard.widgets.find(w => w.id === dataConfigWidgetId)
    : null;

  return (
    <div className={cn('dashboard-builder h-full flex flex-col', className)}>
      {/* Toolbar */}
      {isEditing && (
        <div className="dashboard-toolbar bg-white border-b border-gray-200 p-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <Button
                size="sm"
                onClick={() => setIsGalleryOpen(true)}
              >
                <Plus className="mr-2 h-4 w-4" />
                Add Widget
              </Button>

              <div className="flex items-center space-x-1 ml-4">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={undo}
                  disabled={historyIndex === 0}
                >
                  <Undo className="h-4 w-4" />
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={redo}
                  disabled={historyIndex === history.length - 1}
                >
                  <Redo className="h-4 w-4" />
                </Button>
              </div>
            </div>

            <div className="flex items-center space-x-2">
              <Button variant="outline" size="sm">
                <Eye className="mr-2 h-4 w-4" />
                Preview
              </Button>
              <Button size="sm" onClick={handleSave}>
                <Save className="mr-2 h-4 w-4" />
                Save Dashboard
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Dashboard Grid */}
      <div className="dashboard-content flex-1 overflow-auto p-4">
        {currentDashboard.widgets.length === 0 ? (
          <Card className="h-full flex items-center justify-center">
            <div className="text-center text-gray-500">
              <div className="text-6xl mb-4">📊</div>
              <h3 className="text-lg font-medium mb-2">No widgets yet</h3>
              <p className="text-sm mb-4">Add your first widget to get started</p>
              {isEditing && (
                <Button onClick={() => setIsGalleryOpen(true)}>
                  <Plus className="mr-2 h-4 w-4" />
                  Add Widget
                </Button>
              )}
            </div>
          </Card>
        ) : (
          <ResponsiveGridLayout
            className="layout"
            layouts={layouts}
            breakpoints={currentDashboard.layout.breakpoints}
            cols={{
              lg: currentDashboard.layout.cols,
              md: Math.floor(currentDashboard.layout.cols * 0.75),
              sm: Math.floor(currentDashboard.layout.cols * 0.5),
              xs: Math.floor(currentDashboard.layout.cols * 0.25),
              xxs: 1,
            }}
            rowHeight={currentDashboard.layout.rowHeight}
            margin={currentDashboard.layout.margin}
            containerPadding={currentDashboard.layout.padding}
            isDraggable={isEditing}
            isResizable={isEditing}
            compactType={currentDashboard.settings.compactType}
            onLayoutChange={handleLayoutChange}
            onDragStart={() => setIsDragging(true)}
            onDragStop={() => setIsDragging(false)}
            onResizeStart={() => setIsDragging(true)}
            onResizeStop={() => setIsDragging(false)}
          >
            {currentDashboard.widgets.map(renderWidget)}
          </ResponsiveGridLayout>
        )}
      </div>

      {/* Widget Gallery Modal */}
      <WidgetGallery
        open={isGalleryOpen}
        onClose={() => setIsGalleryOpen(false)}
        onAddWidget={handleAddWidget}
      />

      {/* Widget Data Configuration Modal */}
      {dataConfigWidget && (
        <WidgetDataConfig
          widgetId={dataConfigWidget.id}
          widgetType={dataConfigWidget.type}
          initialConfig={dataConfigWidget.config?.dataBinding as DataBindingConfig | undefined}
          onSave={handleSaveDataConfig}
          onCancel={() => setDataConfigWidgetId(null)}
        />
      )}

      {/* Share Widget Dialog */}
      {shareWidgetId && (() => {
        const widget = currentDashboard.widgets.find(w => w.id === shareWidgetId);
        return widget ? (
          <ShareWidgetDialog
            widget={widget}
            dashboardId={currentDashboard.id}
            isOpen={true}
            onClose={() => setShareWidgetId(null)}
          />
        ) : null;
      })()}

      {/* Fullscreen Widget */}
      {fullscreenWidgetId && (() => {
        const widget = currentDashboard.widgets.find(w => w.id === fullscreenWidgetId);
        return widget ? (
          <FullscreenWidget
            widget={widget}
            data={widgetData[widget.id]}
            isOpen={true}
            onClose={() => setFullscreenWidgetId(null)}
            onRefresh={onWidgetDataRefresh}
          />
        ) : null;
      })()}
    </div>
  );
};

export default DashboardBuilder;