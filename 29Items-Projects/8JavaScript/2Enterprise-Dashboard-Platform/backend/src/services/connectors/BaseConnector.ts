/**
 * Base Connector Interface
 * Abstract class defining the contract for database connectors
 */

export interface ConnectionConfig {
  host: string;
  port: number;
  database: string;
  ssl?: boolean;
  connectionTimeout?: number;
  queryTimeout?: number;
}

export interface TableInfo {
  name: string;
  schema: string;
  type: 'table' | 'view';
  rowCount?: number;
}

export interface ColumnInfo {
  name: string;
  dataType: string;
  isNullable: boolean;
  isPrimaryKey: boolean;
  isForeignKey: boolean;
  defaultValue?: string;
  maxLength?: number;
  precision?: number;
  scale?: number;
  references?: {
    table: string;
    column: string;
  };
}

export interface SchemaInfo {
  name: string;
  tables: TableInfo[];
}

export interface QueryResult {
  rows: Record<string, unknown>[];
  rowCount: number;
  fields: Array<{
    name: string;
    dataType: string;
  }>;
  executionTime: number;
}

export interface TestConnectionResult {
  success: boolean;
  latency: number;
  version?: string;
  error?: string;
}

export abstract class BaseConnector {
  protected config: ConnectionConfig;
  protected credentials: { username: string; password: string };
  protected connected: boolean = false;
  protected connectionId: string;

  constructor(
    connectionId: string,
    config: ConnectionConfig,
    credentials: { username: string; password: string }
  ) {
    this.connectionId = connectionId;
    this.config = config;
    this.credentials = credentials;
  }

  /**
   * Establish connection to the database
   */
  abstract connect(): Promise<void>;

  /**
   * Close the database connection
   */
  abstract disconnect(): Promise<void>;

  /**
   * Test the database connection
   */
  abstract testConnection(): Promise<TestConnectionResult>;

  /**
   * Execute a SQL query
   * @param query - SQL query string
   * @param params - Query parameters for prepared statements
   * @param timeout - Query timeout in milliseconds
   */
  abstract executeQuery(
    query: string,
    params?: unknown[],
    timeout?: number
  ): Promise<QueryResult>;

  /**
   * Get all schemas in the database
   */
  abstract getSchemas(): Promise<SchemaInfo[]>;

  /**
   * Get all tables in a schema
   * @param schema - Schema name (defaults to public/default)
   */
  abstract getTables(schema?: string): Promise<TableInfo[]>;

  /**
   * Get columns for a specific table
   * @param tableName - Table name
   * @param schema - Schema name (defaults to public/default)
   */
  abstract getTableColumns(tableName: string, schema?: string): Promise<ColumnInfo[]>;

  /**
   * Check if connector is currently connected
   */
  isConnected(): boolean {
    return this.connected;
  }

  /**
   * Get the connection ID
   */
  getConnectionId(): string {
    return this.connectionId;
  }

  /**
   * Get a preview of table data
   * @param tableName - Table name
   * @param schema - Schema name
   * @param limit - Number of rows to preview
   */
  async getTablePreview(
    tableName: string,
    schema?: string,
    limit: number = 100
  ): Promise<QueryResult> {
    const schemaPrefix = schema ? `"${schema}".` : '';
    const query = `SELECT * FROM ${schemaPrefix}"${tableName}" LIMIT ${limit}`;
    return this.executeQuery(query);
  }
}

export type ConnectorType = 'POSTGRESQL' | 'MYSQL';
