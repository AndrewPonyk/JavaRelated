/**
 * Query Execution Service
 * Executes queries through connectors with caching and timeout handling
 */

import { connectorFactory, QueryResult, ConnectorType } from '../connectors/index.js';
import { QueryBuilder, VisualQuery, DatabaseDialect } from './QueryBuilder.js';
import { QueryValidator } from './QueryValidator.js';
import { credentialService } from '../security/credentialService.js';
import { ExtendedCacheService } from '../cache/cacheService.js';
import crypto from 'crypto';

export interface QueryExecutionOptions {
  timeout?: number; // Query timeout in ms
  useCache?: boolean; // Whether to use caching
  cacheTtl?: number; // Cache TTL in seconds
  maxRows?: number; // Maximum rows to return
}

export interface QueryExecutionResult extends QueryResult {
  cached: boolean;
  cacheKey?: string;
  validationWarnings?: string[];
}

export class QueryExecutionService {
  private cacheService: ExtendedCacheService;
  private queryValidator: QueryValidator;
  private defaultTimeout: number = 30000; // 30 seconds
  private defaultCacheTtl: number = 300; // 5 minutes
  private maxRows: number = 10000;

  constructor(cacheService: ExtendedCacheService) {
    this.cacheService = cacheService;
    this.queryValidator = new QueryValidator();
  }

  /**
   * Execute a raw SQL query
   */
  async executeRaw(
    connectionId: string,
    connectionType: ConnectorType,
    config: Record<string, unknown>,
    credentials: Record<string, unknown>,
    query: string,
    options: QueryExecutionOptions = {}
  ): Promise<QueryExecutionResult> {
    // Sanitize query
    const sanitizedQuery = this.queryValidator.sanitize(query);

    // Validate query
    const validation = this.queryValidator.validate(sanitizedQuery);
    if (!validation.isValid) {
      throw new Error(`Query validation failed: ${validation.errors.join(', ')}`);
    }

    // Check cache if enabled
    const useCache = options.useCache ?? true;
    let cacheKey: string | undefined;

    if (useCache) {
      cacheKey = this.generateCacheKey(connectionId, sanitizedQuery);
      const cached = await this.cacheService.getQueryResult(cacheKey);
      if (cached) {
        return {
          ...cached,
          cached: true,
          cacheKey,
          validationWarnings: validation.warnings,
        };
      }
    }

    // Decrypt credentials if needed
    const decryptedCredentials = credentialService.getCredentials(credentials);

    // Get connector and execute
    const connector = await connectorFactory.getConnector(
      connectionId,
      connectionType,
      {
        host: config.host as string,
        port: config.port as number,
        database: config.database as string,
        ssl: config.ssl as boolean | undefined,
        connectionTimeout: config.connectionTimeout as number | undefined,
      },
      {
        username: decryptedCredentials.username as string,
        password: decryptedCredentials.password as string,
      }
    );

    // Apply row limit if not already present
    let finalQuery = sanitizedQuery;
    const maxRows = options.maxRows ?? this.maxRows;
    if (!sanitizedQuery.toUpperCase().includes('LIMIT')) {
      finalQuery = `${sanitizedQuery} LIMIT ${maxRows}`;
    }

    const timeout = options.timeout ?? this.defaultTimeout;
    const result = await connector.executeQuery(finalQuery, [], timeout);

    // Cache result if enabled
    if (useCache && cacheKey) {
      const cacheTtl = options.cacheTtl ?? this.defaultCacheTtl;
      await this.cacheService.setQueryResult(cacheKey, result, cacheTtl);
    }

    return {
      ...result,
      cached: false,
      cacheKey,
      validationWarnings: validation.warnings,
    };
  }

  /**
   * Execute a visual query
   */
  async executeVisual(
    connectionId: string,
    connectionType: ConnectorType,
    config: Record<string, unknown>,
    credentials: Record<string, unknown>,
    visualQuery: VisualQuery,
    options: QueryExecutionOptions = {}
  ): Promise<QueryExecutionResult> {
    // Determine dialect
    const dialect: DatabaseDialect =
      connectionType === 'MYSQL' ? 'mysql' : 'postgresql';

    // Build SQL from visual query
    const queryBuilder = new QueryBuilder(dialect);
    const { sql, params } = queryBuilder.build(visualQuery);

    // For execution, we need to interpolate params for now
    // In production, you'd want to use prepared statements
    let interpolatedSql = sql;
    params.forEach((param, index) => {
      const placeholder = dialect === 'mysql' ? '?' : `$${index + 1}`;
      const value = this.formatParamValue(param);
      interpolatedSql = interpolatedSql.replace(placeholder, value);
    });

    return this.executeRaw(
      connectionId,
      connectionType,
      config,
      credentials,
      interpolatedSql,
      options
    );
  }

  /**
   * Get SQL preview for a visual query
   */
  getVisualQueryPreview(
    connectionType: ConnectorType,
    visualQuery: VisualQuery
  ): { sql: string; complexity: string } {
    const dialect: DatabaseDialect =
      connectionType === 'MYSQL' ? 'mysql' : 'postgresql';

    const queryBuilder = new QueryBuilder(dialect);
    const sql = queryBuilder.getPreviewSql(visualQuery);
    const complexity = this.queryValidator.estimateComplexity(sql);

    return { sql, complexity };
  }

  /**
   * Validate a query without executing
   */
  validateQuery(query: string): {
    isValid: boolean;
    errors: string[];
    warnings: string[];
    complexity: string;
  } {
    const sanitized = this.queryValidator.sanitize(query);
    const validation = this.queryValidator.validate(sanitized);
    const complexity = this.queryValidator.estimateComplexity(sanitized);

    return {
      ...validation,
      complexity,
    };
  }

  /**
   * Invalidate cache for a connection
   */
  async invalidateConnectionCache(connectionId: string): Promise<void> {
    await this.cacheService.invalidateQueryResults(connectionId);
  }

  private generateCacheKey(connectionId: string, query: string): string {
    const hash = crypto
      .createHash('md5')
      .update(query.toLowerCase().replace(/\s+/g, ' ').trim())
      .digest('hex');

    return `query:${connectionId}:${hash}`;
  }

  private formatParamValue(value: unknown): string {
    if (value === null) return 'NULL';
    if (typeof value === 'string') return `'${value.replace(/'/g, "''")}'`;
    if (typeof value === 'number') return String(value);
    if (typeof value === 'boolean') return value ? 'TRUE' : 'FALSE';
    if (value instanceof Date) return `'${value.toISOString()}'`;
    if (Array.isArray(value)) {
      return `(${value.map((v) => this.formatParamValue(v)).join(', ')})`;
    }
    return String(value);
  }
}
