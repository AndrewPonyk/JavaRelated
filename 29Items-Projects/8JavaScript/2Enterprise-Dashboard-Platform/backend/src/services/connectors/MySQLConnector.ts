/**
 * MySQL Database Connector
 * Implements BaseConnector for MySQL databases
 */

import mysql from 'mysql2/promise';
import {
  BaseConnector,
  ConnectionConfig,
  QueryResult,
  TestConnectionResult,
  SchemaInfo,
  TableInfo,
  ColumnInfo,
} from './BaseConnector.js';

export class MySQLConnector extends BaseConnector {
  private pool: mysql.Pool | null = null;

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

    this.pool = mysql.createPool({
      host: this.config.host,
      port: this.config.port,
      database: this.config.database,
      user: this.credentials.username,
      password: this.credentials.password,
      ssl: this.config.ssl ? { rejectUnauthorized: false } : undefined,
      connectTimeout: this.config.connectionTimeout || 10000,
      waitForConnections: true,
      connectionLimit: 10,
      queueLimit: 0,
    });

    // Test the connection
    const connection = await this.pool.getConnection();
    connection.release();
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
    let tempPool: mysql.Pool | null = null;

    try {
      tempPool = mysql.createPool({
        host: this.config.host,
        port: this.config.port,
        database: this.config.database,
        user: this.credentials.username,
        password: this.credentials.password,
        ssl: this.config.ssl ? { rejectUnauthorized: false } : undefined,
        connectTimeout: this.config.connectionTimeout || 10000,
        connectionLimit: 1,
      });

      const connection = await tempPool.getConnection();
      const [rows] = await connection.query('SELECT VERSION() as version');
      connection.release();
      await tempPool.end();

      const versionResult = rows as Array<{ version: string }>;

      return {
        success: true,
        latency: Date.now() - startTime,
        version: versionResult[0]?.version,
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
    const connection = await this.pool!.getConnection();

    try {
      // Set query timeout if specified
      if (timeout) {
        await connection.query(`SET SESSION max_execution_time = ${timeout}`);
      }

      const [rows, fields] = await connection.query(query, params);
      const resultRows = rows as Record<string, unknown>[];
      const resultFields = fields as mysql.FieldPacket[];

      return {
        rows: resultRows,
        rowCount: resultRows.length,
        fields: resultFields
          ? resultFields.map((field) => ({
              name: field.name,
              dataType: this.mapMySQLTypeToString(field.type),
            }))
          : [],
        executionTime: Date.now() - startTime,
      };
    } finally {
      // Reset query timeout
      if (timeout) {
        await connection.query('SET SESSION max_execution_time = 0').catch(() => {});
      }
      connection.release();
    }
  }

  async getSchemas(): Promise<SchemaInfo[]> {
    if (!this.pool) {
      await this.connect();
    }

    const query = `
      SELECT schema_name
      FROM information_schema.schemata
      WHERE schema_name NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys')
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

  async getTables(schema?: string): Promise<TableInfo[]> {
    if (!this.pool) {
      await this.connect();
    }

    const dbName = schema || this.config.database;

    const query = `
      SELECT
        t.table_name as name,
        t.table_schema as \`schema\`,
        t.table_type as type,
        t.table_rows as row_count
      FROM information_schema.tables t
      WHERE t.table_schema = ?
      AND t.table_type IN ('BASE TABLE', 'VIEW')
      ORDER BY t.table_name
    `;

    const result = await this.executeQuery(query, [dbName]);

    return result.rows.map((row) => ({
      name: row.name as string,
      schema: row.schema as string,
      type: (row.type as string) === 'BASE TABLE' ? 'table' : 'view',
      rowCount: row.row_count ? Number(row.row_count) : undefined,
    }));
  }

  async getTableColumns(tableName: string, schema?: string): Promise<ColumnInfo[]> {
    if (!this.pool) {
      await this.connect();
    }

    const dbName = schema || this.config.database;

    const query = `
      SELECT
        c.column_name as name,
        c.data_type as data_type,
        c.is_nullable as is_nullable,
        c.column_default as default_value,
        c.character_maximum_length as max_length,
        c.numeric_precision as \`precision\`,
        c.numeric_scale as scale,
        c.column_key as column_key,
        kcu.referenced_table_name as ref_table,
        kcu.referenced_column_name as ref_column
      FROM information_schema.columns c
      LEFT JOIN information_schema.key_column_usage kcu
        ON c.table_schema = kcu.table_schema
        AND c.table_name = kcu.table_name
        AND c.column_name = kcu.column_name
        AND kcu.referenced_table_name IS NOT NULL
      WHERE c.table_name = ?
      AND c.table_schema = ?
      ORDER BY c.ordinal_position
    `;

    const result = await this.executeQuery(query, [tableName, dbName]);

    return result.rows.map((row) => ({
      name: row.name as string,
      dataType: row.data_type as string,
      isNullable: (row.is_nullable as string) === 'YES',
      isPrimaryKey: (row.column_key as string) === 'PRI',
      isForeignKey: row.ref_table !== null,
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

  private mapMySQLTypeToString(typeId: number | undefined): string {
    if (typeId === undefined) return 'unknown';

    // MySQL type constants from mysql2
    const typeMap: Record<number, string> = {
      0: 'decimal',
      1: 'tinyint',
      2: 'smallint',
      3: 'int',
      4: 'float',
      5: 'double',
      6: 'null',
      7: 'timestamp',
      8: 'bigint',
      9: 'mediumint',
      10: 'date',
      11: 'time',
      12: 'datetime',
      13: 'year',
      14: 'newdate',
      15: 'varchar',
      16: 'bit',
      245: 'json',
      246: 'newdecimal',
      247: 'enum',
      248: 'set',
      249: 'tiny_blob',
      250: 'medium_blob',
      251: 'long_blob',
      252: 'blob',
      253: 'var_string',
      254: 'string',
      255: 'geometry',
    };

    return typeMap[typeId] || 'unknown';
  }

  override async getTablePreview(
    tableName: string,
    schema?: string,
    limit: number = 100
  ): Promise<QueryResult> {
    const dbName = schema || this.config.database;
    const query = `SELECT * FROM \`${dbName}\`.\`${tableName}\` LIMIT ${limit}`;
    return this.executeQuery(query);
  }
}
