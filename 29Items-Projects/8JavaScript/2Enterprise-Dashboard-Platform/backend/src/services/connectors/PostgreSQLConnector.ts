/**
 * PostgreSQL Database Connector
 * Implements BaseConnector for PostgreSQL databases
 */

import pg from 'pg';
import {
  BaseConnector,
  ConnectionConfig,
  QueryResult,
  TestConnectionResult,
  SchemaInfo,
  TableInfo,
  ColumnInfo,
} from './BaseConnector.js';

const { Pool } = pg;

export class PostgreSQLConnector extends BaseConnector {
  private pool: pg.Pool | null = null;

  constructor(
    connectionId: string,
    config: ConnectionConfig,
    credentials: { username: string; password: string }
  ) {
    super(connectionId, config, credentials);
  }

  async connect(): Promise<void> {
    if (this.pool) {
      return;
    }

    this.pool = new Pool({
      host: this.config.host,
      port: this.config.port,
      database: this.config.database,
      user: this.credentials.username,
      password: this.credentials.password,
      ssl: this.config.ssl ? { rejectUnauthorized: false } : undefined,
      connectionTimeoutMillis: this.config.connectionTimeout || 10000,
      idleTimeoutMillis: 30000,
      max: 10,
    });

    // Test the connection
    const client = await this.pool.connect();
    client.release();
    this.connected = true;
  }

  async disconnect(): Promise<void> {
    if (this.pool) {
      await this.pool.end();
      this.pool = null;
      this.connected = false;
    }
  }

  async testConnection(): Promise<TestConnectionResult> {
    const startTime = Date.now();
    let tempPool: pg.Pool | null = null;

    try {
      tempPool = new Pool({
        host: this.config.host,
        port: this.config.port,
        database: this.config.database,
        user: this.credentials.username,
        password: this.credentials.password,
        ssl: this.config.ssl ? { rejectUnauthorized: false } : undefined,
        connectionTimeoutMillis: this.config.connectionTimeout || 10000,
        max: 1,
      });

      const client = await tempPool.connect();
      const result = await client.query('SELECT version()');
      client.release();
      await tempPool.end();

      return {
        success: true,
        latency: Date.now() - startTime,
        version: result.rows[0]?.version,
      };
    } catch (error) {
      if (tempPool) {
        await tempPool.end().catch(() => {});
      }
      return {
        success: false,
        latency: Date.now() - startTime,
        error: error instanceof Error ? error.message : 'Unknown error',
      };
    }
  }

  async executeQuery(
    query: string,
    params?: unknown[],
    timeout?: number
  ): Promise<QueryResult> {
    if (!this.pool) {
      await this.connect();
    }

    const startTime = Date.now();
    const client = await this.pool!.connect();

    try {
      // Set statement timeout if specified
      if (timeout) {
        await client.query(`SET statement_timeout = ${timeout}`);
      }

      const result = await client.query(query, params);

      return {
        rows: result.rows,
        rowCount: result.rowCount || 0,
        fields: result.fields.map((field) => ({
          name: field.name,
          dataType: this.mapPgTypeToString(field.dataTypeID),
        })),
        executionTime: Date.now() - startTime,
      };
    } finally {
      // Reset statement timeout
      if (timeout) {
        await client.query('SET statement_timeout = 0').catch(() => {});
      }
      client.release();
    }
  }

  async getSchemas(): Promise<SchemaInfo[]> {
    if (!this.pool) {
      await this.connect();
    }

    const query = `
      SELECT schema_name
      FROM information_schema.schemata
      WHERE schema_name NOT IN ('information_schema', 'pg_catalog', 'pg_toast')
      ORDER BY schema_name
    `;

    const result = await this.executeQuery(query);
    const schemas: SchemaInfo[] = [];

    for (const row of result.rows) {
      const schemaName = row.schema_name as string;
      const tables = await this.getTables(schemaName);
      schemas.push({
        name: schemaName,
        tables,
      });
    }

    return schemas;
  }

  async getTables(schema: string = 'public'): Promise<TableInfo[]> {
    if (!this.pool) {
      await this.connect();
    }

    const query = `
      SELECT
        t.table_name as name,
        t.table_schema as schema,
        t.table_type as type,
        (
          SELECT reltuples::bigint
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
          WHERE c.relname = t.table_name
          AND n.nspname = t.table_schema
        ) as row_count
      FROM information_schema.tables t
      WHERE t.table_schema = $1
      AND t.table_type IN ('BASE TABLE', 'VIEW')
      ORDER BY t.table_name
    `;

    const result = await this.executeQuery(query, [schema]);

    return result.rows.map((row) => ({
      name: row.name as string,
      schema: row.schema as string,
      type: (row.type as string) === 'BASE TABLE' ? 'table' : 'view',
      rowCount: row.row_count ? Number(row.row_count) : undefined,
    }));
  }

  async getTableColumns(tableName: string, schema: string = 'public'): Promise<ColumnInfo[]> {
    if (!this.pool) {
      await this.connect();
    }

    const query = `
      SELECT
        c.column_name as name,
        c.data_type as data_type,
        c.is_nullable as is_nullable,
        c.column_default as default_value,
        c.character_maximum_length as max_length,
        c.numeric_precision as precision,
        c.numeric_scale as scale,
        CASE WHEN pk.column_name IS NOT NULL THEN true ELSE false END as is_primary_key,
        CASE WHEN fk.column_name IS NOT NULL THEN true ELSE false END as is_foreign_key,
        fk.foreign_table_name as ref_table,
        fk.foreign_column_name as ref_column
      FROM information_schema.columns c
      LEFT JOIN (
        SELECT ku.column_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage ku
          ON tc.constraint_name = ku.constraint_name
          AND tc.table_schema = ku.table_schema
        WHERE tc.table_name = $1
        AND tc.table_schema = $2
        AND tc.constraint_type = 'PRIMARY KEY'
      ) pk ON c.column_name = pk.column_name
      LEFT JOIN (
        SELECT
          kcu.column_name,
          ccu.table_name as foreign_table_name,
          ccu.column_name as foreign_column_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
          ON tc.constraint_name = kcu.constraint_name
          AND tc.table_schema = kcu.table_schema
        JOIN information_schema.constraint_column_usage ccu
          ON ccu.constraint_name = tc.constraint_name
          AND ccu.table_schema = tc.table_schema
        WHERE tc.table_name = $1
        AND tc.table_schema = $2
        AND tc.constraint_type = 'FOREIGN KEY'
      ) fk ON c.column_name = fk.column_name
      WHERE c.table_name = $1
      AND c.table_schema = $2
      ORDER BY c.ordinal_position
    `;

    const result = await this.executeQuery(query, [tableName, schema]);

    return result.rows.map((row) => ({
      name: row.name as string,
      dataType: row.data_type as string,
      isNullable: (row.is_nullable as string) === 'YES',
      isPrimaryKey: row.is_primary_key as boolean,
      isForeignKey: row.is_foreign_key as boolean,
      defaultValue: row.default_value as string | undefined,
      maxLength: row.max_length ? Number(row.max_length) : undefined,
      precision: row.precision ? Number(row.precision) : undefined,
      scale: row.scale ? Number(row.scale) : undefined,
      references: row.ref_table
        ? {
            table: row.ref_table as string,
            column: row.ref_column as string,
          }
        : undefined,
    }));
  }

  private mapPgTypeToString(typeId: number): string {
    // Common PostgreSQL type OIDs
    const typeMap: Record<number, string> = {
      16: 'boolean',
      20: 'bigint',
      21: 'smallint',
      23: 'integer',
      25: 'text',
      700: 'real',
      701: 'double precision',
      1043: 'varchar',
      1082: 'date',
      1083: 'time',
      1114: 'timestamp',
      1184: 'timestamptz',
      1700: 'numeric',
      2950: 'uuid',
      3802: 'jsonb',
      114: 'json',
    };

    return typeMap[typeId] || 'unknown';
  }
}
