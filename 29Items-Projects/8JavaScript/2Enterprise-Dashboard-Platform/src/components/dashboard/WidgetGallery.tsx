import React, { useState, useMemo } from 'react';
import { X, Search, Plus, BarChart3, Hash, Type, Globe } from 'lucide-react';
import { WidgetTemplate, widgetTemplates, widgetCategories } from './WidgetRegistry';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { cn } from '@/utils/cn';

interface WidgetGalleryProps {
  open: boolean;
  onClose: () => void;
  onAddWidget: (templateId: string) => void;
}

const WidgetGallery: React.FC<WidgetGalleryProps> = ({
  open,
  onClose,
  onAddWidget,
}) => {
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [searchTerm, setSearchTerm] = useState('');

  const filteredTemplates = useMemo(() => {
    let filtered = widgetTemplates;

    // Filter by category
    if (selectedCategory !== 'all') {
      filtered = filtered.filter(template => template.category === selectedCategory);
    }

    // Filter by search term
    if (searchTerm) {
      filtered = filtered.filter(template =>
        template.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        template.description.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }

    return filtered;
  }, [selectedCategory, searchTerm]);

  const handleAddWidget = (templateId: string) => {
    onAddWidget(templateId);
    onClose();
  };

  const getCategoryIcon = (categoryId: string) => {
    const category = widgetCategories.find(c => c.id === categoryId);
    if (!category) return Hash;
    return category.icon;
  };

  const renderWidgetPreview = (template: WidgetTemplate) => {
    // Simple preview based on widget type
    const getPreviewContent = () => {
      switch (template.type) {
        case 'metric':
          return (
            <div className="text-center py-4">
              <div className="text-2xl font-bold text-blue-600 mb-1">12.5K</div>
              <div className="text-xs text-gray-500">Sample Metric</div>
            </div>
          );

        case 'chart':
          const chartType = template.defaultConfig?.chartType || 'line';
          return (
            <div className="p-2 h-16 flex items-center justify-center">
              {chartType === 'line' && (
                <svg viewBox="0 0 100 40" className="w-full h-full text-blue-500">
                  <path
                    d="M5,30 Q25,10 45,20 T85,15"
                    stroke="currentColor"
                    strokeWidth="2"
                    fill="none"
                  />
                </svg>
              )}
              {chartType === 'bar' && (
                <div className="flex items-end space-x-1 h-full">
                  <div className="bg-blue-500 w-2 h-6"></div>
                  <div className="bg-blue-500 w-2 h-4"></div>
                  <div className="bg-blue-500 w-2 h-8"></div>
                  <div className="bg-blue-500 w-2 h-5"></div>
                </div>
              )}
              {chartType === 'pie' && (
                <div className="w-8 h-8 rounded-full bg-gradient-conic from-blue-500 via-green-500 to-yellow-500"></div>
              )}
            </div>
          );

        case 'table':
          return (
            <div className="p-2 space-y-1">
              <div className="flex space-x-2">
                <div className="bg-gray-200 h-2 w-8 rounded"></div>
                <div className="bg-gray-200 h-2 w-12 rounded"></div>
                <div className="bg-gray-200 h-2 w-6 rounded"></div>
              </div>
              <div className="flex space-x-2">
                <div className="bg-gray-100 h-2 w-8 rounded"></div>
                <div className="bg-gray-100 h-2 w-12 rounded"></div>
                <div className="bg-gray-100 h-2 w-6 rounded"></div>
              </div>
              <div className="flex space-x-2">
                <div className="bg-gray-100 h-2 w-8 rounded"></div>
                <div className="bg-gray-100 h-2 w-12 rounded"></div>
                <div className="bg-gray-100 h-2 w-6 rounded"></div>
              </div>
            </div>
          );

        default:
          return (
            <div className="flex items-center justify-center h-16 text-gray-400">
              <Type className="h-6 w-6" />
            </div>
          );
      }
    };

    return (
      <Card
        key={template.id}
        className="cursor-pointer transition-all duration-200 hover:shadow-md hover:scale-105 group"
        onClick={() => handleAddWidget(template.id)}
      >
        <div className="p-4">
          {/* Preview */}
          <div className="bg-gray-50 rounded-lg mb-3 overflow-hidden">
            {getPreviewContent()}
          </div>

          {/* Template Info */}
          <div className="space-y-2">
            <div className="flex items-start justify-between">
              <h4 className="font-medium text-sm text-gray-900 group-hover:text-blue-600 transition-colors">
                {template.name}
              </h4>
              <Badge variant="secondary" className="text-xs">
                {template.category}
              </Badge>
            </div>

            <p className="text-xs text-gray-600 line-clamp-2">
              {template.description}
            </p>

            {/* Add Button */}
            <Button
              size="sm"
              className="w-full opacity-0 group-hover:opacity-100 transition-opacity"
            >
              <Plus className="mr-1 h-3 w-3" />
              Add Widget
            </Button>
          </div>
        </div>
      </Card>
    );
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm">
      <div className="fixed inset-4 bg-white rounded-lg shadow-2xl overflow-hidden">
        <div className="flex h-full">
          {/* Sidebar */}
          <div className="w-64 bg-gray-50 border-r border-gray-200 p-4">
            <div className="space-y-4">
              <div>
                <h2 className="text-lg font-semibold text-gray-900 mb-4">Widget Gallery</h2>

                {/* Search */}
                <div className="relative mb-4">
                  <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
                  <input
                    type="text"
                    placeholder="Search widgets..."
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    className="w-full pl-10 pr-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  />
                </div>
              </div>

              {/* Categories */}
              <div>
                <h3 className="text-sm font-medium text-gray-700 mb-2">Categories</h3>
                <div className="space-y-1">
                  <button
                    onClick={() => setSelectedCategory('all')}
                    className={cn(
                      'w-full flex items-center space-x-2 px-3 py-2 text-sm rounded-md transition-colors text-left',
                      selectedCategory === 'all'
                        ? 'bg-blue-100 text-blue-700'
                        : 'text-gray-600 hover:bg-gray-100'
                    )}
                  >
                    <BarChart3 className="h-4 w-4" />
                    <span>All Widgets</span>
                    <Badge variant="secondary" className="ml-auto text-xs">
                      {widgetTemplates.length}
                    </Badge>
                  </button>

                  {widgetCategories.map(category => {
                    const Icon = category.icon;
                    const count = widgetTemplates.filter(t => t.category === category.id).length;

                    return (
                      <button
                        key={category.id}
                        onClick={() => setSelectedCategory(category.id)}
                        className={cn(
                          'w-full flex items-center space-x-2 px-3 py-2 text-sm rounded-md transition-colors text-left',
                          selectedCategory === category.id
                            ? 'bg-blue-100 text-blue-700'
                            : 'text-gray-600 hover:bg-gray-100'
                        )}
                      >
                        <Icon className="h-4 w-4" />
                        <span>{category.name}</span>
                        <Badge variant="secondary" className="ml-auto text-xs">
                          {count}
                        </Badge>
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>

          {/* Content */}
          <div className="flex-1 flex flex-col">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-gray-200">
              <div>
                <h3 className="text-lg font-medium text-gray-900">
                  {selectedCategory === 'all'
                    ? 'All Widgets'
                    : widgetCategories.find(c => c.id === selectedCategory)?.name || selectedCategory
                  }
                </h3>
                <p className="text-sm text-gray-600">
                  {filteredTemplates.length} widget{filteredTemplates.length !== 1 ? 's' : ''} available
                </p>
              </div>

              <Button variant="ghost" size="icon" onClick={onClose}>
                <X className="h-4 w-4" />
              </Button>
            </div>

            {/* Widget Grid */}
            <div className="flex-1 overflow-auto p-4">
              {filteredTemplates.length > 0 ? (
                <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                  {filteredTemplates.map(renderWidgetPreview)}
                </div>
              ) : (
                <div className="flex items-center justify-center h-full text-center">
                  <div className="text-gray-400">
                    <div className="text-4xl mb-4">🔍</div>
                    <h4 className="text-lg font-medium mb-2">No widgets found</h4>
                    <p className="text-sm">
                      {searchTerm
                        ? `No widgets match "${searchTerm}"`
                        : 'No widgets available in this category'
                      }
                    </p>
                    {searchTerm && (
                      <Button
                        variant="outline"
                        size="sm"
                        className="mt-4"
                        onClick={() => setSearchTerm('')}
                      >
                        Clear search
                      </Button>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default WidgetGallery;