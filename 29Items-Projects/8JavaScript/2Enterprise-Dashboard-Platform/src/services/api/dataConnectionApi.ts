/**
 * Data Connection API Service
 * Handles all data connection related API calls
 */

import { apiClient } from './apiClient';

// Types
export type DataConnectionType =
  | 'MYSQL'
  | 'POSTGRESQL'
  | 'MONGODB'
  | 'REST_API'
  | 'GRAPHQL_API'
  | 'CSV_FILE'
  | 'JSON_FILE'
  | 'GOOGLE_SHEETS'
  | 'SALESFORCE'
  | 'HUBSPOT'
  | 'SLACK'
  | 'CUSTOM';

export type ConnectionStatus = 'PENDING' | 'CONNECTED' | 'FAILED' | 'DISABLED';

export interface DatabaseConfig {
  host: string;
  port: number;
  database: string;
  ssl?: boolean;
  connectionTimeout?: number;
  queryTimeout?: number;
}

export interface Credentials {
  username: string;
  password: string;
}

export interface DataConnection {
  id: string;
  name: string;
  type: DataConnectionType;
  config: DatabaseConfig;
  isActive: boolean;
  lastTested?: string;
  status: ConnectionStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDataConnectionInput {
  name: string;
  type: DataConnectionType;
  config: DatabaseConfig;
  credentials: Credentials;
}

export interface UpdateDataConnectionInput {
  name?: string;
  config?: Partial<DatabaseConfig>;
  credentials?: Partial<Credentials>;
  isActive?: boolean;
}

export interface TestConnectionResult {
  success: boolean;
  latency: number;
  version?: string;
  error?: string;
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
  references?: {
    table: string;
    column: string;
  };
}

export interface SchemaInfo {
  name: string;
  tables: TableInfo[];
}

export interface VisualQueryColumn {
  table: string;
  column: string;
  alias?: string;
  aggregation?: 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX' | 'COUNT_DISTINCT';
}

export interface VisualQueryJoin {
  type: 'INNER' | 'LEFT' | 'RIGHT' | 'FULL';
  table: string;
  alias?: string;
  on: {
    leftTable: string;
    leftColumn: string;
    rightTable: string;
    rightColumn: string;
  };
}

export interface VisualQueryCondition {
  table: string;
  column: string;
  operator: '=' | '!=' | '>' | '<' | '>=' | '<=' | 'LIKE' | 'NOT LIKE' | 'IN' | 'NOT IN' | 'IS NULL' | 'IS NOT NULL' | 'BETWEEN';
  value?: unknown;
  values?: unknown[];
  logicalOperator?: 'AND' | 'OR';
}

export interface VisualQueryOrderBy {
  table: string;
  column: string;
  direction: 'ASC' | 'DESC';
}

export interface VisualQuery {
  from: {
    table: string;
    schema?: string;
    alias?: string;
  };
  columns: VisualQueryColumn[];
  joins?: VisualQueryJoin[];
  where?: VisualQueryCondition[];
  groupBy?: Array<{ table: string; column: string }>;
  having?: VisualQueryCondition[];
  orderBy?: VisualQueryOrderBy[];
  limit?: number;
  offset?: number;
}

export interface ExecuteQueryInput {
  type: 'raw' | 'visual';
  rawQuery?: string;
  visualQuery?: VisualQuery;
  options?: {
    timeout?: number;
    useCache?: boolean;
    cacheTtl?: number;
    maxRows?: number;
  };
}

export interface QueryResult {
  rows: Record<string, unknown>[];
  rowCount: number;
  fields: Array<{ name: string; dataType: string }>;
  executionTime: number;
  cached: boolean;
  validationWarnings?: string[];
}

export interface SavedQuery {
  id: string;
  name: string;
  queryType: 'VISUAL' | 'RAW';
  visualQuery?: VisualQuery;
  rawQuery?: string;
  connectionId: string;
  userId: string;
  createdAt: string;
  updatedAt: string;
}

export interface PaginatedResponse<T> {
  success: boolean;
  data: T[];
  pagination: {
    page: number;
    limit: number;
    total: number;
    totalPages: number;
  };
}

// API Functions

/**
 * Get all data connections for the authenticated user
 */
export async function getDataConnections(params?: {
  page?: number;
  limit?: number;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}): Promise<PaginatedResponse<DataConnection>> {
  const response = await apiClient.get('/data-connections', { params });
  return response.data;
}

/**
 * Get a specific data connection
 */
export async function getDataConnection(id: string): Promise<{ success: boolean; data: DataConnection }> {
  const response = await apiClient.get(`/data-connections/${id}`);
  return response.data;
}

/**
 * Create a new data connection
 */
export async function createDataConnection(input: CreateDataConnectionInput): Promise<{ success: boolean; data: DataConnection; message: string }> {
  const response = await apiClient.post('/data-connections', input);
  return response.data;
}

/**
 * Update a data connection
 */
export async function updateDataConnection(
  id: string,
  input: UpdateDataConnectionInput
): Promise<{ success: boolean; data: DataConnection; message: string }> {
  const response = await apiClient.put(`/data-connections/${id}`, input);
  return response.data;
}

/**
 * Delete a data connection
 */
export async function deleteDataConnection(id: string): Promise<{ success: boolean; message: string }> {
  const response = await apiClient.delete(`/data-connections/${id}`);
  return response.data;
}

/**
 * Test a data connection
 */
export async function testDataConnection(id: string): Promise<{ success: boolean; data: TestConnectionResult }> {
  const response = await apiClient.post(`/data-connections/${id}/test`);
  return response.data;
}

/**
 * Get database schema for a connection
 */
export async function getConnectionSchema(id: string): Promise<{ success: boolean; data: SchemaInfo[]; cached: boolean }> {
  const response = await apiClient.get(`/data-connections/${id}/schema`);
  return response.data;
}

/**
 * Get tables for a connection
 */
export async function getConnectionTables(
  id: string,
  schema?: string
): Promise<{ success: boolean; data: TableInfo[] }> {
  const response = await apiClient.get(`/data-connections/${id}/tables`, {
    params: schema ? { schema } : undefined,
  });
  return response.data;
}

/**
 * Get columns for a table
 */
export async function getTableColumns(
  connectionId: string,
  tableName: string,
  schema?: string
): Promise<{ success: boolean; data: ColumnInfo[] }> {
  const response = await apiClient.get(`/data-connections/${connectionId}/tables/${tableName}/columns`, {
    params: schema ? { schema } : undefined,
  });
  return response.data;
}

/**
 * Execute a query
 */
export async function executeQuery(
  connectionId: string,
  input: ExecuteQueryInput
): Promise<{ success: boolean; data: QueryResult }> {
  const response = await apiClient.post(`/data-connections/${connectionId}/query`, input);
  return response.data;
}

/**
 * Get saved queries for a connection
 */
export async function getSavedQueries(connectionId: string): Promise<{ success: boolean; data: SavedQuery[] }> {
  const response = await apiClient.get(`/data-connections/${connectionId}/queries`);
  return response.data;
}

/**
 * Save a query
 */
export async function saveQuery(
  connectionId: string,
  input: {
    name: string;
    queryType: 'VISUAL' | 'RAW';
    visualQuery?: VisualQuery;
    rawQuery?: string;
  }
): Promise<{ success: boolean; data: SavedQuery; message: string }> {
  const response = await apiClient.post(`/data-connections/${connectionId}/queries`, input);
  return response.data;
}

/**
 * Delete a saved query
 */
export async function deleteSavedQuery(
  connectionId: string,
  queryId: string
): Promise<{ success: boolean; message: string }> {
  const response = await apiClient.delete(`/data-connections/${connectionId}/queries/${queryId}`);
  return response.data;
}

// Export default object with all functions
export default {
  getDataConnections,
  getDataConnection,
  createDataConnection,
  updateDataConnection,
  deleteDataConnection,
  testDataConnection,
  getConnectionSchema,
  getConnectionTables,
  getTableColumns,
  executeQuery,
  getSavedQueries,
  saveQuery,
  deleteSavedQuery,
};
