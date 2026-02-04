/**
 * Visual Query Builder Component
 * Allows users to build SQL queries visually without writing SQL
 */

import React, { useState, useEffect } from 'react';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { cn } from '@/utils/cn';
import {
  getConnectionTables,
  getTableColumns,
  TableInfo,
  ColumnInfo,
  VisualQuery,
  VisualQueryColumn,
  VisualQueryJoin,
  VisualQueryCondition,
  VisualQueryOrderBy,
} from '@/services/api/dataConnectionApi';

interface QueryBuilderProps {
  connectionId: string;
  initialQuery?: VisualQuery;
  onChange: (query: VisualQuery) => void;
  onPreview?: () => void;
}

type Aggregation = 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX' | 'COUNT_DISTINCT' | undefined;

const aggregations: { value: Aggregation; label: string }[] = [
  { value: undefined, label: 'None' },
  { value: 'COUNT', label: 'COUNT' },
  { value: 'SUM', label: 'SUM' },
  { value: 'AVG', label: 'AVG' },
  { value: 'MIN', label: 'MIN' },
  { value: 'MAX', label: 'MAX' },
  { value: 'COUNT_DISTINCT', label: 'COUNT DISTINCT' },
];

const operators = [
  { value: '=', label: 'equals' },
  { value: '!=', label: 'not equals' },
  { value: '>', label: 'greater than' },
  { value: '<', label: 'less than' },
  { value: '>=', label: 'greater or equal' },
  { value: '<=', label: 'less or equal' },
  { value: 'LIKE', label: 'contains' },
  { value: 'IS NULL', label: 'is null' },
  { value: 'IS NOT NULL', label: 'is not null' },
];

export function QueryBuilder({
  connectionId,
  initialQuery,
  onChange,
  onPreview,
}: QueryBuilderProps) {
  // Data state
  const [tables, setTables] = useState<TableInfo[]>([]);
  const [tableColumns, setTableColumns] = useState<Record<string, ColumnInfo[]>>({});
  const [isLoadingTables, setIsLoadingTables] = useState(false);

  // Query state
  const [fromTable, setFromTable] = useState<string>('');
  const [fromSchema, setFromSchema] = useState<string>('public');
  const [selectedColumns, setSelectedColumns] = useState<VisualQueryColumn[]>([]);
  const [joins, setJoins] = useState<VisualQueryJoin[]>([]);
  const [conditions, setConditions] = useState<VisualQueryCondition[]>([]);
  const [orderBy, setOrderBy] = useState<VisualQueryOrderBy[]>([]);
  const [limit, setLimit] = useState<number>(100);

  // Load tables on mount
  useEffect(() => {
    loadTables();
  }, [connectionId]);

  // Initialize from initial query
  useEffect(() => {
    if (initialQuery) {
      setFromTable(initialQuery.from.table);
      setFromSchema(initialQuery.from.schema || 'public');
      setSelectedColumns(initialQuery.columns);
      setJoins(initialQuery.joins || []);
      setConditions(initialQuery.where || []);
      setOrderBy(initialQuery.orderBy || []);
      setLimit(initialQuery.limit || 100);
    }
  }, [initialQuery]);

  // Update parent when query changes
  useEffect(() => {
    if (fromTable) {
      const query: VisualQuery = {
        from: { table: fromTable, schema: fromSchema },
        columns: selectedColumns.length > 0 ? selectedColumns : [{ table: fromTable, column: '*' }],
        joins: joins.length > 0 ? joins : undefined,
        where: conditions.length > 0 ? conditions : undefined,
        orderBy: orderBy.length > 0 ? orderBy : undefined,
        limit,
      };
      onChange(query);
    }
  }, [fromTable, fromSchema, selectedColumns, joins, conditions, orderBy, limit]);

  const loadTables = async () => {
    setIsLoadingTables(true);
    try {
      const result = await getConnectionTables(connectionId);
      setTables(result.data);
    } catch (error) {
      console.error('Failed to load tables:', error);
    } finally {
      setIsLoadingTables(false);
    }
  };

  const loadColumns = async (tableName: string) => {
    if (tableColumns[tableName]) return;

    try {
      const result = await getTableColumns(connectionId, tableName, fromSchema);
      setTableColumns((prev) => ({ ...prev, [tableName]: result.data }));
    } catch (error) {
      console.error('Failed to load columns:', error);
    }
  };

  const handleTableSelect = async (tableName: string) => {
    setFromTable(tableName);
    setSelectedColumns([]);
    setJoins([]);
    setConditions([]);
    setOrderBy([]);
    await loadColumns(tableName);
  };

  const toggleColumn = (column: ColumnInfo, aggregation?: Aggregation) => {
    const existing = selectedColumns.find(
      (c) => c.table === fromTable && c.column === column.name
    );

    if (existing) {
      setSelectedColumns(selectedColumns.filter((c) => c !== existing));
    } else {
      setSelectedColumns([
        ...selectedColumns,
        { table: fromTable, column: column.name, aggregation },
      ]);
    }
  };

  const updateColumnAggregation = (index: number, aggregation: Aggregation) => {
    const newColumns = [...selectedColumns];
    newColumns[index] = { ...newColumns[index], aggregation };
    setSelectedColumns(newColumns);
  };

  const addCondition = () => {
    if (!fromTable || !tableColumns[fromTable]?.[0]) return;

    setConditions([
      ...conditions,
      {
        table: fromTable,
        column: tableColumns[fromTable][0].name,
        operator: '=',
        value: '',
        logicalOperator: conditions.length > 0 ? 'AND' : undefined,
      },
    ]);
  };

  const updateCondition = (index: number, updates: Partial<VisualQueryCondition>) => {
    const newConditions = [...conditions];
    newConditions[index] = { ...newConditions[index], ...updates };
    setConditions(newConditions);
  };

  const removeCondition = (index: number) => {
    setConditions(conditions.filter((_, i) => i !== index));
  };

  const addOrderBy = () => {
    if (!fromTable || !tableColumns[fromTable]?.[0]) return;

    setOrderBy([
      ...orderBy,
      { table: fromTable, column: tableColumns[fromTable][0].name, direction: 'ASC' },
    ]);
  };

  const updateOrderBy = (index: number, updates: Partial<VisualQueryOrderBy>) => {
    const newOrderBy = [...orderBy];
    newOrderBy[index] = { ...newOrderBy[index], ...updates };
    setOrderBy(newOrderBy);
  };

  const removeOrderBy = (index: number) => {
    setOrderBy(orderBy.filter((_, i) => i !== index));
  };

  const currentTableColumns = fromTable ? tableColumns[fromTable] || [] : [];

  return (
    <div className="space-y-4">
      {/* Table Selection */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Select Table</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoadingTables ? (
            <div className="text-sm text-muted-foreground">Loading tables...</div>
          ) : (
            <select
              value={fromTable}
              onChange={(e) => handleTableSelect(e.target.value)}
              className="w-full px-3 py-2 border rounded-md bg-background"
            >
              <option value="">Select a table...</option>
              {tables.map((table) => (
                <option key={table.name} value={table.name}>
                  {table.schema}.{table.name}
                  {table.rowCount !== undefined && ` (~${table.rowCount} rows)`}
                </option>
              ))}
            </select>
          )}
        </CardContent>
      </Card>

      {/* Column Selection */}
      {fromTable && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Select Columns</CardTitle>
          </CardHeader>
          <CardContent>
            {currentTableColumns.length === 0 ? (
              <div className="text-sm text-muted-foreground">Loading columns...</div>
            ) : (
              <div className="space-y-2">
                {currentTableColumns.map((column) => {
                  const selectedIndex = selectedColumns.findIndex(
                    (c) => c.table === fromTable && c.column === column.name
                  );
                  const isSelected = selectedIndex !== -1;

                  return (
                    <div
                      key={column.name}
                      className={cn(
                        'flex items-center justify-between p-2 border rounded-md',
                        isSelected ? 'border-primary bg-primary/5' : ''
                      )}
                    >
                      <div className="flex items-center gap-2">
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => toggleColumn(column)}
                          className="h-4 w-4 rounded"
                        />
                        <span className="font-medium">{column.name}</span>
                        <span className="text-xs text-muted-foreground">
                          {column.dataType}
                        </span>
                        {column.isPrimaryKey && (
                          <span className="text-xs bg-yellow-500/20 text-yellow-700 px-1 rounded">
                            PK
                          </span>
                        )}
                      </div>

                      {isSelected && (
                        <select
                          value={selectedColumns[selectedIndex].aggregation || ''}
                          onChange={(e) =>
                            updateColumnAggregation(
                              selectedIndex,
                              (e.target.value || undefined) as Aggregation
                            )
                          }
                          className="text-sm px-2 py-1 border rounded bg-background"
                        >
                          {aggregations.map((agg) => (
                            <option key={agg.label} value={agg.value || ''}>
                              {agg.label}
                            </option>
                          ))}
                        </select>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Filters / WHERE */}
      {fromTable && (
        <Card>
          <CardHeader className="pb-3 flex flex-row items-center justify-between">
            <CardTitle className="text-base">Filters (WHERE)</CardTitle>
            <Button type="button" variant="outline" size="sm" onClick={addCondition}>
              Add Filter
            </Button>
          </CardHeader>
          <CardContent>
            {conditions.length === 0 ? (
              <div className="text-sm text-muted-foreground">No filters applied</div>
            ) : (
              <div className="space-y-2">
                {conditions.map((condition, index) => (
                  <div key={index} className="flex items-center gap-2">
                    {index > 0 && (
                      <select
                        value={condition.logicalOperator || 'AND'}
                        onChange={(e) =>
                          updateCondition(index, {
                            logicalOperator: e.target.value as 'AND' | 'OR',
                          })
                        }
                        className="w-20 px-2 py-1 border rounded bg-background text-sm"
                      >
                        <option value="AND">AND</option>
                        <option value="OR">OR</option>
                      </select>
                    )}

                    <select
                      value={condition.column}
                      onChange={(e) => updateCondition(index, { column: e.target.value })}
                      className="flex-1 px-2 py-1 border rounded bg-background text-sm"
                    >
                      {currentTableColumns.map((col) => (
                        <option key={col.name} value={col.name}>
                          {col.name}
                        </option>
                      ))}
                    </select>

                    <select
                      value={condition.operator}
                      onChange={(e) =>
                        updateCondition(index, {
                          operator: e.target.value as VisualQueryCondition['operator'],
                        })
                      }
                      className="w-32 px-2 py-1 border rounded bg-background text-sm"
                    >
                      {operators.map((op) => (
                        <option key={op.value} value={op.value}>
                          {op.label}
                        </option>
                      ))}
                    </select>

                    {!['IS NULL', 'IS NOT NULL'].includes(condition.operator) && (
                      <input
                        type="text"
                        value={String(condition.value || '')}
                        onChange={(e) => updateCondition(index, { value: e.target.value })}
                        placeholder="Value"
                        className="flex-1 px-2 py-1 border rounded bg-background text-sm"
                      />
                    )}

                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => removeCondition(index)}
                    >
                      X
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Order By */}
      {fromTable && (
        <Card>
          <CardHeader className="pb-3 flex flex-row items-center justify-between">
            <CardTitle className="text-base">Sort (ORDER BY)</CardTitle>
            <Button type="button" variant="outline" size="sm" onClick={addOrderBy}>
              Add Sort
            </Button>
          </CardHeader>
          <CardContent>
            {orderBy.length === 0 ? (
              <div className="text-sm text-muted-foreground">No sorting applied</div>
            ) : (
              <div className="space-y-2">
                {orderBy.map((sort, index) => (
                  <div key={index} className="flex items-center gap-2">
                    <select
                      value={sort.column}
                      onChange={(e) => updateOrderBy(index, { column: e.target.value })}
                      className="flex-1 px-2 py-1 border rounded bg-background text-sm"
                    >
                      {currentTableColumns.map((col) => (
                        <option key={col.name} value={col.name}>
                          {col.name}
                        </option>
                      ))}
                    </select>

                    <select
                      value={sort.direction}
                      onChange={(e) =>
                        updateOrderBy(index, { direction: e.target.value as 'ASC' | 'DESC' })
                      }
                      className="w-32 px-2 py-1 border rounded bg-background text-sm"
                    >
                      <option value="ASC">Ascending</option>
                      <option value="DESC">Descending</option>
                    </select>

                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => removeOrderBy(index)}
                    >
                      X
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Limit */}
      {fromTable && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Limit Results</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2">
              <input
                type="number"
                value={limit}
                onChange={(e) => setLimit(parseInt(e.target.value) || 100)}
                min={1}
                max={10000}
                className="w-32 px-3 py-2 border rounded-md bg-background"
              />
              <span className="text-sm text-muted-foreground">rows (max 10,000)</span>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Preview Button */}
      {fromTable && onPreview && (
        <div className="flex justify-end">
          <Button type="button" onClick={onPreview}>
            Preview Results
          </Button>
        </div>
      )}
    </div>
  );
}

export default QueryBuilder;
