/**
 * Widget Data Service
 * Handles fetching and transforming data for widgets based on their data binding configuration
 */

import { PrismaClient } from '@prisma/client';
import { ExtendedCacheService } from '../cache/cacheService.js';
import { QueryExecutionService } from '../query/QueryExecutionService.js';
import { connectorFactory, ConnectorType } from '../connectors/index.js';
import { VisualQuery } from '../query/QueryBuilder.js';
import { logger } from '../../utils/logger.js';
import { AppError } from '../../utils/errors.js';

export interface DataBindingConfig {
  connectionId: string;
  queryType: 'raw' | 'visual';
  rawQuery?: string;
  visualQuery?: VisualQuery;
  refreshInterval?: number; // in seconds
  fieldMappings?: Record<string, string>; // widget field -> query column
  transformations?: Array<{
    field: string;
    type: 'number' | 'date' | 'string' | 'boolean';
    format?: string;
  }>;
}

export interface WidgetDataResult {
  data: Record<string, unknown>[];
  metadata: {
    rowCount: number;
    executionTime: number;
    cached: boolean;
    lastRefresh: string;
    nextRefresh?: string;
  };
}

export class WidgetDataService {
  private prisma: PrismaClient;
  private cacheService: ExtendedCacheService;
  private queryExecutionService: QueryExecutionService;

  constructor(prisma: PrismaClient, cacheService: ExtendedCacheService) {
    this.prisma = prisma;
    this.cacheService = cacheService;
    this.queryExecutionService = new QueryExecutionService(cacheService);
  }

  /**
   * Fetch data for a widget based on its configuration
   */
  async getWidgetData(
    widgetId: string,
    userId: string,
    forceRefresh: boolean = false
  ): Promise<WidgetDataResult> {
    // Get widget with data source configuration
    const widget = await this.prisma.widget.findFirst({
      where: { id: widgetId, userId },
      include: { dashboard: true },
    });

    if (!widget) {
      throw new AppError('Widget not found', 404);
    }

    // Check if widget has data binding configuration
    const config = widget.config as Record<string, unknown>;
    const dataBinding = config.dataBinding as DataBindingConfig | undefined;

    if (!dataBinding || !dataBinding.connectionId) {
      // Return cached data if available, otherwise empty
      if (widget.cachedData) {
        return {
          data: widget.cachedData as Record<string, unknown>[],
          metadata: {
            rowCount: (widget.cachedData as unknown[]).length,
            executionTime: 0,
            cached: true,
            lastRefresh: widget.lastFetch?.toISOString() || new Date().toISOString(),
          },
        };
      }
      return {
        data: [],
        metadata: {
          rowCount: 0,
          executionTime: 0,
          cached: false,
          lastRefresh: new Date().toISOString(),
        },
      };
    }

    // Check cache if not forcing refresh
    if (!forceRefresh) {
      const cacheKey = `widget_data:${widgetId}`;
      const cachedResult = await this.cacheService.getWidgetData(cacheKey);
      if (cachedResult) {
        return cachedResult as WidgetDataResult;
      }
    }

    // Get data connection
    const connection = await this.prisma.dataConnection.findFirst({
      where: { id: dataBinding.connectionId, userId },
    });

    if (!connection) {
      throw new AppError('Data connection not found or access denied', 404);
    }

    if (!connectorFactory.isTypeSupported(connection.type)) {
      throw new AppError(`Connection type ${connection.type} is not supported`, 400);
    }

    const connectionConfig = connection.config as Record<string, unknown>;

    // Execute query
    let queryResult;
    const cacheTtl = dataBinding.refreshInterval || 300; // Default 5 min

    if (dataBinding.queryType === 'raw' && dataBinding.rawQuery) {
      queryResult = await this.queryExecutionService.executeRaw(
        connection.id,
        connection.type as ConnectorType,
        connectionConfig,
        connection.credentials as Record<string, unknown>,
        dataBinding.rawQuery,
        { useCache: true, cacheTtl }
      );
    } else if (dataBinding.queryType === 'visual' && dataBinding.visualQuery) {
      queryResult = await this.queryExecutionService.executeVisual(
        connection.id,
        connection.type as ConnectorType,
        connectionConfig,
        connection.credentials as Record<string, unknown>,
        dataBinding.visualQuery,
        { useCache: true, cacheTtl }
      );
    } else {
      throw new AppError('Invalid data binding configuration', 400);
    }

    // Apply field mappings and transformations
    let processedData = queryResult.rows;

    if (dataBinding.fieldMappings) {
      processedData = this.applyFieldMappings(processedData, dataBinding.fieldMappings);
    }

    if (dataBinding.transformations) {
      processedData = this.applyTransformations(processedData, dataBinding.transformations);
    }

    // Build result
    const now = new Date();
    const result: WidgetDataResult = {
      data: processedData,
      metadata: {
        rowCount: queryResult.rowCount,
        executionTime: queryResult.executionTime,
        cached: queryResult.cached,
        lastRefresh: now.toISOString(),
        nextRefresh: dataBinding.refreshInterval
          ? new Date(now.getTime() + dataBinding.refreshInterval * 1000).toISOString()
          : undefined,
      },
    };

    // Update widget cached data
    await this.prisma.widget.update({
      where: { id: widgetId },
      data: {
        cachedData: processedData as any,
        lastFetch: now,
      },
    });

    // Cache the result
    const cacheKey = `widget_data:${widgetId}`;
    await this.cacheService.setWidgetData(cacheKey, result, cacheTtl);

    logger.info('Fetched widget data', {
      widgetId,
      rowCount: result.metadata.rowCount,
      executionTime: result.metadata.executionTime,
      cached: result.metadata.cached,
    });

    return result;
  }

  /**
   * Force refresh widget data
   */
  async refreshWidgetData(widgetId: string, userId: string): Promise<WidgetDataResult> {
    // Invalidate cache
    const cacheKey = `widget_data:${widgetId}`;
    await this.cacheService.delete(cacheKey);

    // Fetch fresh data
    return this.getWidgetData(widgetId, userId, true);
  }

  /**
   * Update widget data binding configuration
   */
  async updateDataBinding(
    widgetId: string,
    userId: string,
    dataBinding: DataBindingConfig
  ): Promise<void> {
    const widget = await this.prisma.widget.findFirst({
      where: { id: widgetId, userId },
    });

    if (!widget) {
      throw new AppError('Widget not found', 404);
    }

    // Verify connection access
    if (dataBinding.connectionId) {
      const connection = await this.prisma.dataConnection.findFirst({
        where: { id: dataBinding.connectionId, userId },
      });

      if (!connection) {
        throw new AppError('Data connection not found or access denied', 404);
      }
    }

    // Update widget config with new data binding
    const currentConfig = widget.config as Record<string, unknown>;
    const newConfig = {
      ...currentConfig,
      dataBinding,
    };

    await this.prisma.widget.update({
      where: { id: widgetId },
      data: {
        config: newConfig as any,
        dataSource: dataBinding.connectionId,
        refreshRate: dataBinding.refreshInterval,
      },
    });

    // Invalidate cache
    const cacheKey = `widget_data:${widgetId}`;
    await this.cacheService.delete(cacheKey);

    logger.info('Updated widget data binding', { widgetId, connectionId: dataBinding.connectionId });
  }

  /**
   * Get data binding configuration for a widget
   */
  async getDataBinding(widgetId: string, userId: string): Promise<DataBindingConfig | null> {
    const widget = await this.prisma.widget.findFirst({
      where: { id: widgetId, userId },
    });

    if (!widget) {
      throw new AppError('Widget not found', 404);
    }

    const config = widget.config as Record<string, unknown>;
    return (config.dataBinding as DataBindingConfig) || null;
  }

  /**
   * Apply field mappings to transform column names
   */
  private applyFieldMappings(
    data: Record<string, unknown>[],
    mappings: Record<string, string>
  ): Record<string, unknown>[] {
    // Invert mappings: query column -> widget field
    const invertedMappings: Record<string, string> = {};
    for (const [widgetField, queryColumn] of Object.entries(mappings)) {
      invertedMappings[queryColumn] = widgetField;
    }

    return data.map((row) => {
      const mappedRow: Record<string, unknown> = {};
      for (const [key, value] of Object.entries(row)) {
        const mappedKey = invertedMappings[key] || key;
        mappedRow[mappedKey] = value;
      }
      return mappedRow;
    });
  }

  /**
   * Apply transformations to data fields
   */
  private applyTransformations(
    data: Record<string, unknown>[],
    transformations: DataBindingConfig['transformations']
  ): Record<string, unknown>[] {
    if (!transformations?.length) return data;

    return data.map((row) => {
      const transformedRow = { ...row };

      for (const transform of transformations) {
        const value = transformedRow[transform.field];
        if (value === null || value === undefined) continue;

        switch (transform.type) {
          case 'number':
            transformedRow[transform.field] = Number(value);
            break;
          case 'date':
            transformedRow[transform.field] = transform.format
              ? this.formatDate(new Date(String(value)), transform.format)
              : new Date(String(value)).toISOString();
            break;
          case 'string':
            transformedRow[transform.field] = String(value);
            break;
          case 'boolean':
            transformedRow[transform.field] = Boolean(value);
            break;
        }
      }

      return transformedRow;
    });
  }

  /**
   * Format date with simple format string
   */
  private formatDate(date: Date, format: string): string {
    const pad = (n: number) => n.toString().padStart(2, '0');

    return format
      .replace('YYYY', date.getFullYear().toString())
      .replace('MM', pad(date.getMonth() + 1))
      .replace('DD', pad(date.getDate()))
      .replace('HH', pad(date.getHours()))
      .replace('mm', pad(date.getMinutes()))
      .replace('ss', pad(date.getSeconds()));
  }
}

// Factory function
export const createWidgetDataService = (
  prisma: PrismaClient,
  cacheService: ExtendedCacheService
) => {
  return new WidgetDataService(prisma, cacheService);
};
