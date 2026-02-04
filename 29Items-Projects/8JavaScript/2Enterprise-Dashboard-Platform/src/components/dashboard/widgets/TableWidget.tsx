import React, { useState, useMemo } from 'react';
import { ChevronUp, ChevronDown, Search, Filter, ArrowUpDown } from 'lucide-react';
import { Widget, WidgetData, TableColumn } from '@/types/dashboard';
import { BaseWidget, WidgetContainer } from '@/components/dashboard/BaseWidget';
import { Button } from '@/components/ui/Button';
import { cn } from '@/utils/cn';

interface TableWidgetProps {
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
}

interface TableData {
  columns: TableColumn[];
  rows: Record<string, any>[];
  totalRows?: number;
}

type SortDirection = 'asc' | 'desc' | null;

export const TableWidget: React.FC<TableWidgetProps> = ({
  widget,
  data,
  ...baseProps
}) => {
  const tableData = data?.data as TableData | undefined;
  const config = widget.config;

  const [sortBy, setSortBy] = useState<string | null>(null);
  const [sortDirection, setSortDirection] = useState<SortDirection>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [filterColumn, setFilterColumn] = useState<string | null>(null);
  const [filterValue, setFilterValue] = useState('');

  const pageSize = config.pageSize || 10;
  const showPagination = config.pagination !== false;
  const showSearch = config.filterable !== false;

  // Get columns with fallback
  const columns = useMemo(() => {
    if (config.columns?.length) {
      return config.columns;
    }

    // Auto-generate columns from data
    if (tableData?.rows?.length) {
      return Object.keys(tableData.rows[0]).map(key => ({
        key,
        title: key.charAt(0).toUpperCase() + key.slice(1).replace(/_/g, ' '),
        sortable: true,
        filterable: true,
      }));
    }

    return [];
  }, [config.columns, tableData?.rows]);

  // Process and filter data
  const processedData = useMemo(() => {
    if (!tableData?.rows) return [];

    let filteredData = [...tableData.rows];

    // Apply search filter
    if (searchTerm) {
      filteredData = filteredData.filter(row =>
        columns.some(col => {
          const value = row[col.key];
          return value?.toString().toLowerCase().includes(searchTerm.toLowerCase());
        })
      );
    }

    // Apply column filter
    if (filterColumn && filterValue) {
      filteredData = filteredData.filter(row => {
        const value = row[filterColumn];
        return value?.toString().toLowerCase().includes(filterValue.toLowerCase());
      });
    }

    // Apply sorting
    if (sortBy && sortDirection) {
      filteredData.sort((a, b) => {
        const aVal = a[sortBy];
        const bVal = b[sortBy];

        // Handle different data types
        if (typeof aVal === 'number' && typeof bVal === 'number') {
          return sortDirection === 'asc' ? aVal - bVal : bVal - aVal;
        }

        const aStr = aVal?.toString() || '';
        const bStr = bVal?.toString() || '';

        if (sortDirection === 'asc') {
          return aStr.localeCompare(bStr);
        } else {
          return bStr.localeCompare(aStr);
        }
      });
    }

    return filteredData;
  }, [tableData?.rows, columns, searchTerm, sortBy, sortDirection, filterColumn, filterValue]);

  // Paginate data
  const paginatedData = useMemo(() => {
    if (!showPagination) return processedData;

    const startIndex = (currentPage - 1) * pageSize;
    const endIndex = startIndex + pageSize;
    return processedData.slice(startIndex, endIndex);
  }, [processedData, currentPage, pageSize, showPagination]);

  const totalPages = Math.ceil(processedData.length / pageSize);

  const handleSort = (columnKey: string) => {
    const column = columns.find(col => col.key === columnKey);
    if (!column?.sortable) return;

    if (sortBy === columnKey) {
      if (sortDirection === 'asc') {
        setSortDirection('desc');
      } else if (sortDirection === 'desc') {
        setSortBy(null);
        setSortDirection(null);
      } else {
        setSortDirection('asc');
      }
    } else {
      setSortBy(columnKey);
      setSortDirection('asc');
    }
  };

  const formatCellValue = (value: any, column: TableColumn) => {
    if (value === null || value === undefined) return '—';

    if (column.formatter) {
      return column.formatter(value);
    }

    // Default formatting for common data types
    if (typeof value === 'number') {
      return value.toLocaleString();
    }

    if (typeof value === 'boolean') {
      return value ? '✓' : '✗';
    }

    if (value instanceof Date) {
      return value.toLocaleDateString();
    }

    return value.toString();
  };

  const getSortIcon = (columnKey: string) => {
    if (sortBy !== columnKey) {
      return <ArrowUpDown className="h-3 w-3 opacity-50" />;
    }

    return sortDirection === 'asc'
      ? <ChevronUp className="h-3 w-3" />
      : <ChevronDown className="h-3 w-3" />;
  };

  const renderPagination = () => {
    if (!showPagination || totalPages <= 1) return null;

    const maxVisiblePages = 5;
    const startPage = Math.max(1, currentPage - Math.floor(maxVisiblePages / 2));
    const endPage = Math.min(totalPages, startPage + maxVisiblePages - 1);

    return (
      <div className="flex items-center justify-between px-2 py-3 border-t border-gray-200">
        <div className="flex items-center text-sm text-gray-700">
          <span>
            Showing {((currentPage - 1) * pageSize) + 1} to{' '}
            {Math.min(currentPage * pageSize, processedData.length)} of{' '}
            {processedData.length} entries
          </span>
        </div>

        <div className="flex items-center space-x-1">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setCurrentPage(1)}
            disabled={currentPage === 1}
          >
            First
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setCurrentPage(currentPage - 1)}
            disabled={currentPage === 1}
          >
            Previous
          </Button>

          {Array.from({ length: endPage - startPage + 1 }, (_, i) => {
            const pageNum = startPage + i;
            return (
              <Button
                key={pageNum}
                variant={currentPage === pageNum ? 'default' : 'outline'}
                size="sm"
                onClick={() => setCurrentPage(pageNum)}
                className="min-w-[2rem]"
              >
                {pageNum}
              </Button>
            );
          })}

          <Button
            variant="outline"
            size="sm"
            onClick={() => setCurrentPage(currentPage + 1)}
            disabled={currentPage === totalPages}
          >
            Next
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setCurrentPage(totalPages)}
            disabled={currentPage === totalPages}
          >
            Last
          </Button>
        </div>
      </div>
    );
  };

  return (
    <BaseWidget widget={widget} data={data} {...baseProps}>
      <WidgetContainer
        loading={data?.loading}
        error={data?.error}
        empty={!tableData?.rows?.length}
        emptyMessage="No table data available"
      >
        <div className="h-full flex flex-col">
          {/* Search and Filter Controls */}
          {showSearch && (
            <div className="flex items-center space-x-2 p-2 border-b border-gray-200">
              <div className="relative flex-1">
                <Search className="absolute left-2 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
                <input
                  type="text"
                  placeholder="Search table..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="w-full pl-8 pr-3 py-1 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                />
              </div>
            </div>
          )}

          {/* Table */}
          <div className="flex-1 overflow-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200 sticky top-0">
                <tr>
                  {columns.map((column) => (
                    <th
                      key={column.key}
                      className={cn(
                        "px-3 py-2 text-left font-medium text-gray-700",
                        column.sortable && "cursor-pointer hover:bg-gray-100 select-none"
                      )}
                      style={{ width: column.width }}
                      onClick={() => column.sortable && handleSort(column.key)}
                    >
                      <div className="flex items-center space-x-1">
                        <span>{column.title}</span>
                        {column.sortable && getSortIcon(column.key)}
                      </div>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {paginatedData.map((row, rowIndex) => (
                  <tr
                    key={rowIndex}
                    className="border-b border-gray-100 hover:bg-gray-50"
                  >
                    {columns.map((column) => (
                      <td
                        key={column.key}
                        className="px-3 py-2 text-gray-900"
                        style={{ width: column.width }}
                      >
                        {formatCellValue(row[column.key], column)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Empty State */}
            {paginatedData.length === 0 && !data?.loading && (
              <div className="text-center py-8 text-gray-500">
                {searchTerm || filterValue ? 'No matching results found' : 'No data available'}
              </div>
            )}
          </div>

          {/* Pagination */}
          {renderPagination()}
        </div>
      </WidgetContainer>
    </BaseWidget>
  );
};

export default TableWidget;