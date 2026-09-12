import { Pool, type PoolClient, type QueryResultRow } from 'pg';

export interface Queryable {
  query<Row extends QueryResultRow = QueryResultRow>(
    text: string,
    values?: readonly unknown[],
  ): Promise<{ rows: Row[]; rowCount: number | null }>;
}

export interface TransactionalPool extends Queryable {
  connect(): Promise<PoolClient>;
  end(): Promise<void>;
}

export class Database {
  constructor(
    private readonly pool: TransactionalPool,
    private readonly enforceRowLevelSecurity = true,
  ) {}

  query<Row extends QueryResultRow = QueryResultRow>(text: string, values?: readonly unknown[]) {
    return this.pool.query<Row>(text, values);
  }

  async withOwner<T>(ownerId: string, operation: (client: Queryable) => Promise<T>): Promise<T> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      if (this.enforceRowLevelSecurity) {
        await client.query("SELECT set_config('app.user_id', $1, true)", [ownerId]);
      }
      const result = await operation(client);
      await client.query('COMMIT');
      return result;
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  close(): Promise<void> {
    return this.pool.end();
  }
}

export function createDatabase(databaseUrl: string): Database {
  return new Database(
    new Pool({
      connectionString: databaseUrl,
      max: 10,
      idleTimeoutMillis: 10_000,
      connectionTimeoutMillis: 5_000,
    }),
  );
}
