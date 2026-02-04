/**
 * Query Builder Service
 * Converts visual query JSON to SQL statements
 */

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
  values?: unknown[]; // For IN, NOT IN, BETWEEN
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
  groupBy?: Array<{
    table: string;
    column: string;
  }>;
  having?: VisualQueryCondition[];
  orderBy?: VisualQueryOrderBy[];
  limit?: number;
  offset?: number;
}

export type DatabaseDialect = 'postgresql' | 'mysql';

export class QueryBuilder {
  private dialect: DatabaseDialect;
  private paramIndex: number = 0;
  private params: unknown[] = [];

  constructor(dialect: DatabaseDialect = 'postgresql') {
    this.dialect = dialect;
  }

  /**
   * Build SQL from visual query
   */
  build(query: VisualQuery): { sql: string; params: unknown[] } {
    this.paramIndex = 0;
    this.params = [];

    const parts: string[] = [];

    // SELECT clause
    parts.push(this.buildSelect(query.columns));

    // FROM clause
    parts.push(this.buildFrom(query.from));

    // JOIN clauses
    if (query.joins?.length) {
      parts.push(this.buildJoins(query.joins));
    }

    // WHERE clause
    if (query.where?.length) {
      parts.push(this.buildWhere(query.where));
    }

    // GROUP BY clause
    if (query.groupBy?.length) {
      parts.push(this.buildGroupBy(query.groupBy));
    }

    // HAVING clause
    if (query.having?.length) {
      parts.push(this.buildHaving(query.having));
    }

    // ORDER BY clause
    if (query.orderBy?.length) {
      parts.push(this.buildOrderBy(query.orderBy));
    }

    // LIMIT clause
    if (query.limit !== undefined) {
      parts.push(`LIMIT ${query.limit}`);
    }

    // OFFSET clause
    if (query.offset !== undefined) {
      parts.push(`OFFSET ${query.offset}`);
    }

    return {
      sql: parts.join('\n'),
      params: this.params,
    };
  }

  private buildSelect(columns: VisualQueryColumn[]): string {
    if (!columns.length) {
      return 'SELECT *';
    }

    const selectParts = columns.map((col) => {
      const tableRef = this.quoteIdentifier(col.table);
      const columnRef = this.quoteIdentifier(col.column);
      const fullRef = `${tableRef}.${columnRef}`;

      let expression: string;

      if (col.aggregation) {
        if (col.aggregation === 'COUNT_DISTINCT') {
          expression = `COUNT(DISTINCT ${fullRef})`;
        } else {
          expression = `${col.aggregation}(${fullRef})`;
        }
      } else {
        expression = fullRef;
      }

      if (col.alias) {
        expression += ` AS ${this.quoteIdentifier(col.alias)}`;
      }

      return expression;
    });

    return `SELECT ${selectParts.join(', ')}`;
  }

  private buildFrom(from: VisualQuery['from']): string {
    let tableRef = '';

    if (from.schema) {
      tableRef = `${this.quoteIdentifier(from.schema)}.${this.quoteIdentifier(from.table)}`;
    } else {
      tableRef = this.quoteIdentifier(from.table);
    }

    if (from.alias) {
      tableRef += ` AS ${this.quoteIdentifier(from.alias)}`;
    }

    return `FROM ${tableRef}`;
  }

  private buildJoins(joins: VisualQueryJoin[]): string {
    return joins
      .map((join) => {
        const joinType = join.type;
        const tableRef = this.quoteIdentifier(join.table);
        const alias = join.alias ? ` AS ${this.quoteIdentifier(join.alias)}` : '';

        const onClause = `${this.quoteIdentifier(join.on.leftTable)}.${this.quoteIdentifier(join.on.leftColumn)} = ${this.quoteIdentifier(join.on.rightTable)}.${this.quoteIdentifier(join.on.rightColumn)}`;

        return `${joinType} JOIN ${tableRef}${alias} ON ${onClause}`;
      })
      .join('\n');
  }

  private buildWhere(conditions: VisualQueryCondition[]): string {
    if (!conditions.length) return '';

    const whereParts = conditions.map((cond, index) => {
      const conditionStr = this.buildCondition(cond);
      if (index === 0) {
        return conditionStr;
      }
      return `${cond.logicalOperator || 'AND'} ${conditionStr}`;
    });

    return `WHERE ${whereParts.join(' ')}`;
  }

  private buildCondition(cond: VisualQueryCondition): string {
    const columnRef = `${this.quoteIdentifier(cond.table)}.${this.quoteIdentifier(cond.column)}`;

    switch (cond.operator) {
      case 'IS NULL':
        return `${columnRef} IS NULL`;
      case 'IS NOT NULL':
        return `${columnRef} IS NOT NULL`;
      case 'IN':
      case 'NOT IN': {
        const placeholders = (cond.values || []).map(() => this.addParam(cond.values?.shift()));
        return `${columnRef} ${cond.operator} (${placeholders.join(', ')})`;
      }
      case 'BETWEEN': {
        const [min, max] = cond.values || [];
        return `${columnRef} BETWEEN ${this.addParam(min)} AND ${this.addParam(max)}`;
      }
      case 'LIKE':
      case 'NOT LIKE':
        return `${columnRef} ${cond.operator} ${this.addParam(cond.value)}`;
      default:
        return `${columnRef} ${cond.operator} ${this.addParam(cond.value)}`;
    }
  }

  private buildGroupBy(groupBy: Array<{ table: string; column: string }>): string {
    const groupParts = groupBy.map(
      (g) => `${this.quoteIdentifier(g.table)}.${this.quoteIdentifier(g.column)}`
    );
    return `GROUP BY ${groupParts.join(', ')}`;
  }

  private buildHaving(conditions: VisualQueryCondition[]): string {
    if (!conditions.length) return '';

    const havingParts = conditions.map((cond, index) => {
      const conditionStr = this.buildCondition(cond);
      if (index === 0) {
        return conditionStr;
      }
      return `${cond.logicalOperator || 'AND'} ${conditionStr}`;
    });

    return `HAVING ${havingParts.join(' ')}`;
  }

  private buildOrderBy(orderBy: VisualQueryOrderBy[]): string {
    const orderParts = orderBy.map(
      (o) => `${this.quoteIdentifier(o.table)}.${this.quoteIdentifier(o.column)} ${o.direction}`
    );
    return `ORDER BY ${orderParts.join(', ')}`;
  }

  private quoteIdentifier(identifier: string): string {
    // Escape any existing quotes
    const escaped = identifier.replace(/"/g, '""');
    return this.dialect === 'mysql' ? `\`${escaped}\`` : `"${escaped}"`;
  }

  private addParam(value: unknown): string {
    this.params.push(value);
    this.paramIndex++;
    return this.dialect === 'mysql' ? '?' : `$${this.paramIndex}`;
  }

  /**
   * Get SQL preview without parameter placeholders (for display only)
   */
  getPreviewSql(query: VisualQuery): string {
    const { sql, params } = this.build(query);

    let previewSql = sql;
    params.forEach((param, index) => {
      const placeholder = this.dialect === 'mysql' ? '?' : `$${index + 1}`;
      const value = this.formatValueForPreview(param);
      previewSql = previewSql.replace(placeholder, value);
    });

    return previewSql;
  }

  private formatValueForPreview(value: unknown): string {
    if (value === null) return 'NULL';
    if (typeof value === 'string') return `'${value.replace(/'/g, "''")}'`;
    if (typeof value === 'number') return String(value);
    if (typeof value === 'boolean') return value ? 'TRUE' : 'FALSE';
    if (value instanceof Date) return `'${value.toISOString()}'`;
    return String(value);
  }
}
