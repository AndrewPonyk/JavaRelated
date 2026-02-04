import React, { ReactNode, useState, useCallback } from 'react';
import { MoreVertical, RefreshCw, Maximize2, Settings, Trash2, Copy, Share } from 'lucide-react';
import { Widget, WidgetData } from '@/types/dashboard';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/DropdownMenu';
import { cn } from '@/utils/cn';

interface BaseWidgetProps {
  widget: Widget;
  data?: WidgetData;
  isEditing?: boolean;
  isSelected?: boolean;
  isDragging?: boolean;
  onUpdate?: (widget: Widget) => void;
  onDelete?: (widgetId: string) => void;
  onDuplicate?: (widget: Widget) => void;
  onRefresh?: (widgetId: string) => void;
  onSettings?: (widget: Widget) => void;
  onShare?: (widget: Widget) => void;
  onFullscreen?: (widget: Widget) => void;
  children: ReactNode;
  className?: string;
}

export const BaseWidget: React.FC<BaseWidgetProps> = ({
  widget,
  data,
  isEditing = false,
  isSelected = false,
  isDragging = false,
  onUpdate,
  onDelete,
  onDuplicate,
  onRefresh,
  onSettings,
  onShare,
  onFullscreen,
  children,
  className,
}) => {
  const [isLoading, setIsLoading] = useState(false);

  const handleRefresh = useCallback(async () => {
    if (!onRefresh) return;
    setIsLoading(true);
    try {
      await onRefresh(widget.id);
    } finally {
      setIsLoading(false);
    }
  }, [onRefresh, widget.id]);

  const getStatusIndicator = () => {
    if (isLoading || data?.loading) {
      return (
        <div className="flex items-center text-xs text-blue-600">
          <RefreshCw className="h-3 w-3 animate-spin mr-1" />
          Loading...
        </div>
      );
    }

    if (data?.error) {
      return (
        <div className="flex items-center text-xs text-red-600">
          <div className="h-2 w-2 bg-red-500 rounded-full mr-1" />
          Error
        </div>
      );
    }

    if (data?.timestamp) {
      const lastUpdated = new Date(data.timestamp);
      const now = new Date();
      const diffInMinutes = Math.floor((now.getTime() - lastUpdated.getTime()) / 1000 / 60);

      return (
        <div className="flex items-center text-xs text-green-600">
          <div className="h-2 w-2 bg-green-500 rounded-full mr-1" />
          {diffInMinutes === 0 ? 'Just updated' : `${diffInMinutes}m ago`}
        </div>
      );
    }

    return null;
  };

  return (
    <Card
      className={cn(
        'h-full flex flex-col transition-all duration-200',
        'hover:shadow-md',
        isSelected && 'ring-2 ring-blue-500 ring-offset-1',
        isDragging && 'shadow-lg rotate-1 scale-105',
        data?.error && 'border-red-200 bg-red-50',
        className
      )}
      style={{
        backgroundColor: widget.config.backgroundColor,
        color: widget.config.textColor,
      }}
    >
      {/* Widget Header */}
      <div className="flex items-center justify-between p-3 pb-2 border-b border-gray-100">
        <div className="flex-1 min-w-0">
          <h3
            className={cn(
              'font-semibold truncate',
              widget.config.fontSize === 'sm' && 'text-sm',
              widget.config.fontSize === 'lg' && 'text-lg',
              !widget.config.fontSize && 'text-base'
            )}
            title={widget.title}
          >
            {widget.title}
          </h3>
          {widget.description && (
            <p className="text-xs text-gray-500 truncate mt-0.5" title={widget.description}>
              {widget.description}
            </p>
          )}
        </div>

        <div className="flex items-center space-x-1 ml-2">
          {/* Status Indicator */}
          {getStatusIndicator()}

          {/* Widget Actions */}
          {isEditing && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-6 w-6 opacity-0 group-hover:opacity-100 transition-opacity"
                >
                  <MoreVertical className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {onRefresh && (
                  <DropdownMenuItem onClick={handleRefresh} disabled={isLoading}>
                    <RefreshCw className={cn("mr-2 h-4 w-4", isLoading && "animate-spin")} />
                    Refresh Data
                  </DropdownMenuItem>
                )}
                {onSettings && (
                  <DropdownMenuItem onClick={() => onSettings(widget)}>
                    <Settings className="mr-2 h-4 w-4" />
                    Settings
                  </DropdownMenuItem>
                )}
                {onFullscreen && (
                  <DropdownMenuItem onClick={() => onFullscreen(widget)}>
                    <Maximize2 className="mr-2 h-4 w-4" />
                    Fullscreen
                  </DropdownMenuItem>
                )}
                <DropdownMenuSeparator />
                {onDuplicate && (
                  <DropdownMenuItem onClick={() => onDuplicate(widget)}>
                    <Copy className="mr-2 h-4 w-4" />
                    Duplicate
                  </DropdownMenuItem>
                )}
                {onShare && (
                  <DropdownMenuItem onClick={() => onShare(widget)}>
                    <Share className="mr-2 h-4 w-4" />
                    Share
                  </DropdownMenuItem>
                )}
                <DropdownMenuSeparator />
                {onDelete && (
                  <DropdownMenuItem
                    onClick={() => onDelete(widget.id)}
                    className="text-red-600 focus:text-red-600"
                  >
                    <Trash2 className="mr-2 h-4 w-4" />
                    Delete
                  </DropdownMenuItem>
                )}
              </DropdownMenuContent>
            </DropdownMenu>
          )}
        </div>
      </div>

      {/* Widget Content */}
      <div className="flex-1 overflow-hidden">
        {data?.error ? (
          <div className="p-4 text-center">
            <div className="text-red-500 text-sm mb-2">Failed to load data</div>
            <div className="text-xs text-gray-500 mb-3">{data.error}</div>
            {onRefresh && (
              <Button size="sm" variant="outline" onClick={handleRefresh} disabled={isLoading}>
                <RefreshCw className={cn("mr-2 h-3 w-3", isLoading && "animate-spin")} />
                Retry
              </Button>
            )}
          </div>
        ) : (
          <div className="h-full p-3 pt-2">
            {children}
          </div>
        )}
      </div>

      {/* Resize Handle (only in editing mode) */}
      {isEditing && (
        <div className="absolute bottom-0 right-0 w-3 h-3 cursor-se-resize opacity-0 hover:opacity-100 transition-opacity">
          <div className="absolute bottom-0 right-0 w-2 h-2 bg-gray-400 transform rotate-45" />
        </div>
      )}
    </Card>
  );
};

// Widget Container Component for consistent spacing and behavior
interface WidgetContainerProps {
  children: ReactNode;
  className?: string;
  loading?: boolean;
  error?: string;
  empty?: boolean;
  emptyMessage?: string;
}

export const WidgetContainer: React.FC<WidgetContainerProps> = ({
  children,
  className,
  loading,
  error,
  empty,
  emptyMessage = 'No data available',
}) => {
  if (loading) {
    return (
      <div className={cn('flex items-center justify-center h-full', className)}>
        <div className="flex flex-col items-center space-y-2 text-gray-500">
          <RefreshCw className="h-6 w-6 animate-spin" />
          <span className="text-sm">Loading...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={cn('flex items-center justify-center h-full p-4', className)}>
        <div className="text-center text-gray-500">
          <div className="text-red-500 text-sm mb-1">Error loading data</div>
          <div className="text-xs">{error}</div>
        </div>
      </div>
    );
  }

  if (empty) {
    return (
      <div className={cn('flex items-center justify-center h-full', className)}>
        <div className="text-center text-gray-400">
          <div className="text-sm">{emptyMessage}</div>
        </div>
      </div>
    );
  }

  return <div className={cn('h-full', className)}>{children}</div>;
};

export default BaseWidget;