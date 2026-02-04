/**
 * Query Preview Component
 * Shows SQL preview and query results
 */

import React, { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { cn } from '@/utils/cn';
import {
  executeQuery,
  VisualQuery,
  QueryResult,
  ExecuteQueryInput,
} from '@/services/api/dataConnectionApi';

interface QueryPreviewProps {
  connectionId: string;
  visualQuery?: VisualQuery;
  rawQuery?: string;
  queryType: 'visual' | 'raw';
  onQueryChange?: (query: string) => void;
}

export function QueryPreview({
  connectionId,
  visualQuery,
  rawQuery,
  queryType,
  onQueryChange,
}: QueryPreviewProps) {
  const [activeTab, setActiveTab] = useState<'sql' | 'results'>('sql');
  const [isExecuting, setIsExecuting] = useState(false);
  const [result, setResult] = useState<QueryResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [localRawQuery, setLocalRawQuery] = useState(rawQuery || '');

  // Generate SQL preview from visual query (simplified version)
  const getSqlPreview = (): string => {
    if (queryType === 'raw') {
      return localRawQuery;
    }

    if (!visualQuery) return '';

    const parts: string[] = [];

    // SELECT
    const columns = visualQuery.columns.map((col) => {
      let colStr = `"${col.table}"."${col.column}"`;
      if (col.aggregation) {
        if (col.aggregation === 'COUNT_DISTINCT') {
          colStr = `COUNT(DISTINCT ${colStr})`;
        } else {
          colStr = `${col.aggregation}(${colStr})`;
        }
      }
      if (col.alias) {
        colStr += ` AS "${col.alias}"`;
      }
      return colStr;
    });
    parts.push(`SELECT ${columns.join(', ')}`);

    // FROM
    let fromClause = `FROM "${visualQuery.from.schema || 'public'}"."${visualQuery.from.table}"`;
    if (visualQuery.from.alias) {
      fromClause += ` AS "${visualQuery.from.alias}"`;
    }
    parts.push(fromClause);

    // JOINs
    if (visualQuery.joins?.length) {
      for (const join of visualQuery.joins) {
        const joinStr = `${join.type} JOIN "${join.table}"${
          join.alias ? ` AS "${join.alias}"` : ''
        } ON "${join.on.leftTable}"."${join.on.leftColumn}" = "${join.on.rightTable}"."${join.on.rightColumn}"`;
        parts.push(joinStr);
      }
    }

    // WHERE
    if (visualQuery.where?.length) {
      const conditions = visualQuery.where.map((cond, i) => {
        let condStr = '';
        if (i > 0) {
          condStr = `${cond.logicalOperator || 'AND'} `;
        }
        condStr += `"${cond.table}"."${cond.column}" ${cond.operator}`;
        if (!['IS NULL', 'IS NOT NULL'].includes(cond.operator)) {
          condStr += ` '${cond.value}'`;
        }
        return condStr;
      });
      parts.push(`WHERE ${conditions.join(' ')}`);
    }

    // GROUP BY
    if (visualQuery.groupBy?.length) {
      const groupCols = visualQuery.groupBy.map((g) => `"${g.table}"."${g.column}"`);
      parts.push(`GROUP BY ${groupCols.join(', ')}`);
    }

    // ORDER BY
    if (visualQuery.orderBy?.length) {
      const orderCols = visualQuery.orderBy.map(
        (o) => `"${o.table}"."${o.column}" ${o.direction}`
      );
      parts.push(`ORDER BY ${orderCols.join(', ')}`);
    }

    // LIMIT
    if (visualQuery.limit) {
      parts.push(`LIMIT ${visualQuery.limit}`);
    }

    // OFFSET
    if (visualQuery.offset) {
      parts.push(`OFFSET ${visualQuery.offset}`);
    }

    return parts.join('\n');
  };

  const handleExecute = async () => {
    setIsExecuting(true);
    setError(null);
    setResult(null);

    try {
      const input: ExecuteQueryInput =
        queryType === 'visual'
          ? { type: 'visual', visualQuery }
          : { type: 'raw', rawQuery: localRawQuery };

      const response = await executeQuery(connectionId, input);
      setResult(response.data);
      setActiveTab('results');
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to execute query';
      setError(errorMessage);
    } finally {
      setIsExecuting(false);
    }
  };

  const handleRawQueryChange = (value: string) => {
    setLocalRawQuery(value);
    onQueryChange?.(value);
  };

  const sqlPreview = getSqlPreview();

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base">Query Preview</CardTitle>
          <div className="flex items-center gap-2">
            <div className="flex border rounded-md">
              <button
                type="button"
                onClick={() => setActiveTab('sql')}
                className={cn(
                  'px-3 py-1 text-sm transition-colors',
                  activeTab === 'sql'
                    ? 'bg-primary text-primary-foreground'
                    : 'hover:bg-muted'
                )}
              >
                SQL
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('results')}
                className={cn(
                  'px-3 py-1 text-sm transition-colors',
                  activeTab === 'results'
                    ? 'bg-primary text-primary-foreground'
                    : 'hover:bg-muted'
                )}
              >
                Results
              </button>
            </div>
            <Button
              type="button"
              size="sm"
              onClick={handleExecute}
              disabled={isExecuting || (!visualQuery && !localRawQuery)}
            >
              {isExecuting ? 'Executing...' : 'Run Query'}
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        {activeTab === 'sql' && (
          <div className="space-y-2">
            {queryType === 'raw' ? (
              <textarea
                value={localRawQuery}
                onChange={(e) => handleRawQueryChange(e.target.value)}
                placeholder="Enter your SQL query here..."
                className="w-full h-48 px-3 py-2 font-mono text-sm border rounded-md bg-muted/50"
              />
            ) : (
              <pre className="p-4 font-mono text-sm bg-muted/50 rounded-md overflow-auto max-h-64">
                {sqlPreview || 'Select a table and columns to see SQL preview'}
              </pre>
            )}
          </div>
        )}

        {activeTab === 'results' && (
          <div className="space-y-2">
            {error && (
              <div className="p-3 rounded-md bg-red-500/10 text-red-700 dark:text-red-400 text-sm">
                {error}
              </div>
            )}

            {result && (
              <>
                {/* Metadata */}
                <div className="flex items-center gap-4 text-sm text-muted-foreground mb-2">
                  <span>{result.rowCount} rows</span>
                  <span>{result.executionTime}ms</span>
                  {result.cached && (
                    <span className="text-green-600 dark:text-green-400">Cached</span>
                  )}
                </div>

                {/* Warnings */}
                {result.validationWarnings?.length ? (
                  <div className="p-2 rounded-md bg-yellow-500/10 text-yellow-700 dark:text-yellow-400 text-xs mb-2">
                    {result.validationWarnings.map((w, i) => (
                      <div key={i}>{w}</div>
                    ))}
                  </div>
                ) : null}

                {/* Results Table */}
                <div className="border rounded-md overflow-auto max-h-96">
                  <table className="w-full text-sm">
                    <thead className="bg-muted/50 sticky top-0">
                      <tr>
                        {result.fields.map((field) => (
                          <th
                            key={field.name}
                            className="px-3 py-2 text-left font-medium border-b"
                          >
                            <div>{field.name}</div>
                            <div className="text-xs text-muted-foreground font-normal">
                              {field.dataType}
                            </div>
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {result.rows.length === 0 ? (
                        <tr>
                          <td
                            colSpan={result.fields.length}
                            className="px-3 py-4 text-center text-muted-foreground"
                          >
                            No results
                          </td>
                        </tr>
                      ) : (
                        result.rows.map((row, i) => (
                          <tr key={i} className="hover:bg-muted/30">
                            {result.fields.map((field) => (
                              <td key={field.name} className="px-3 py-2 border-b">
                                {formatCellValue(row[field.name])}
                              </td>
                            ))}
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </>
            )}

            {!result && !error && (
              <div className="p-8 text-center text-muted-foreground">
                Click "Run Query" to execute and see results
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function formatCellValue(value: unknown): React.ReactNode {
  if (value === null) {
    return <span className="text-muted-foreground italic">NULL</span>;
  }
  if (value === undefined) {
    return <span className="text-muted-foreground italic">undefined</span>;
  }
  if (typeof value === 'boolean') {
    return <span className={value ? 'text-green-600' : 'text-red-600'}>{String(value)}</span>;
  }
  if (typeof value === 'object') {
    return (
      <span className="font-mono text-xs">{JSON.stringify(value).substring(0, 100)}</span>
    );
  }
  return String(value);
}

export default QueryPreview;
