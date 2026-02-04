import React, { useEffect, useCallback } from 'react';
import { X, Minimize2, RefreshCw } from 'lucide-react';
import { Dialog, DialogContent } from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { Widget, WidgetData } from '@/types/dashboard';
import { getWidgetComponent } from './WidgetRegistry';

interface FullscreenWidgetProps {
  widget: Widget;
  data?: WidgetData;
  isOpen: boolean;
  onClose: () => void;
  onRefresh?: (widgetId: string) => void;
}

export const FullscreenWidget: React.FC<FullscreenWidgetProps> = ({
  widget,
  data,
  isOpen,
  onClose,
  onRefresh,
}) => {
  const WidgetComponent = getWidgetComponent(widget.type);

  // Handle escape key to close
  const handleKeyDown = useCallback((e: KeyboardEvent) => {
    if (e.key === 'Escape') {
      onClose();
    }
  }, [onClose]);

  useEffect(() => {
    if (isOpen) {
      document.addEventListener('keydown', handleKeyDown);
      return () => document.removeEventListener('keydown', handleKeyDown);
    }
  }, [isOpen, handleKeyDown]);

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="max-w-[95vw] max-h-[95vh] w-[95vw] h-[90vh] p-0">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b bg-white">
          <div>
            <h2 className="text-xl font-semibold">{widget.title}</h2>
            {widget.description && (
              <p className="text-sm text-gray-500 mt-1">{widget.description}</p>
            )}
          </div>
          <div className="flex items-center gap-2">
            {onRefresh && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => onRefresh(widget.id)}
              >
                <RefreshCw className="h-4 w-4" />
              </Button>
            )}
            <Button variant="outline" size="sm" onClick={onClose}>
              <Minimize2 className="mr-1 h-4 w-4" />
              Exit Fullscreen
            </Button>
            <Button variant="ghost" size="icon" onClick={onClose}>
              <X className="h-5 w-5" />
            </Button>
          </div>
        </div>

        {/* Widget Content */}
        <div className="flex-1 p-6 overflow-auto bg-gray-50" style={{ height: 'calc(90vh - 80px)' }}>
          <div className="h-full bg-white rounded-lg shadow-sm p-4">
            <WidgetComponent
              widget={widget}
              data={data}
              isEditing={false}
              isSelected={false}
              isDragging={false}
              onUpdate={() => {}}
              onDelete={() => {}}
              onDuplicate={() => {}}
            />
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default FullscreenWidget;
