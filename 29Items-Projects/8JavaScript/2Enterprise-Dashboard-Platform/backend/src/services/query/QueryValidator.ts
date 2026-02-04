/**
 * Query Validator Service
 * Validates SQL queries for security and compliance
 */

export interface ValidationResult {
  isValid: boolean;
  errors: string[];
  warnings: string[];
}

export class QueryValidator {
  // Keywords that indicate dangerous operations
  private static readonly DANGEROUS_KEYWORDS = [
    'DROP',
    'DELETE',
    'TRUNCATE',
    'ALTER',
    'CREATE',
    'INSERT',
    'UPDATE',
    'GRANT',
    'REVOKE',
    'EXEC',
    'EXECUTE',
    'CALL',
    'INTO OUTFILE',
    'INTO DUMPFILE',
    'LOAD_FILE',
    'LOAD DATA',
    'REPLACE INTO',
    'MERGE',
  ];

  // Common SQL injection patterns
  private static readonly INJECTION_PATTERNS = [
    /;\s*--/gi, // Statement terminator followed by comment
    /;\s*\/\*/gi, // Statement terminator followed by block comment
    /'\s*OR\s*'?\d*'?\s*=\s*'?\d*'?/gi, // OR '1'='1' pattern
    /'\s*OR\s*''='/gi, // OR ''='' pattern
    /UNION\s+(ALL\s+)?SELECT/gi, // UNION SELECT injection
    /WAITFOR\s+DELAY/gi, // Time-based injection
    /BENCHMARK\s*\(/gi, // MySQL benchmark injection
    /SLEEP\s*\(/gi, // MySQL sleep injection
    /pg_sleep\s*\(/gi, // PostgreSQL sleep injection
    /INFORMATION_SCHEMA\./gi, // Schema probing (warning)
  ];

  // Allowed query types
  private static readonly ALLOWED_QUERY_STARTS = ['SELECT', 'WITH'];

  /**
   * Validate a SQL query
   */
  validate(query: string): ValidationResult {
    const errors: string[] = [];
    const warnings: string[] = [];

    // Normalize query for checking
    const normalizedQuery = query.toUpperCase().trim();

    // Check if query starts with allowed statement
    const startsWithAllowed = QueryValidator.ALLOWED_QUERY_STARTS.some((start) =>
      normalizedQuery.startsWith(start)
    );

    if (!startsWithAllowed) {
      errors.push('Only SELECT queries are allowed');
    }

    // Check for dangerous keywords
    for (const keyword of QueryValidator.DANGEROUS_KEYWORDS) {
      const pattern = new RegExp(`\\b${keyword}\\b`, 'gi');
      if (pattern.test(normalizedQuery)) {
        errors.push(`Dangerous keyword detected: ${keyword}`);
      }
    }

    // Check for SQL injection patterns
    for (const pattern of QueryValidator.INJECTION_PATTERNS) {
      if (pattern.test(query)) {
        // INFORMATION_SCHEMA is a warning, not an error
        if (pattern.source.includes('INFORMATION_SCHEMA')) {
          warnings.push('Query accesses system catalog tables');
        } else {
          errors.push(`Potential SQL injection pattern detected`);
        }
      }
    }

    // Check for multiple statements (semicolon not at end)
    const semicolonMatches = query.match(/;/g);
    if (semicolonMatches && semicolonMatches.length > 1) {
      errors.push('Multiple SQL statements are not allowed');
    } else if (
      semicolonMatches &&
      semicolonMatches.length === 1 &&
      !query.trim().endsWith(';')
    ) {
      errors.push('Semicolon found in middle of query - multiple statements not allowed');
    }

    // Check for excessive subqueries (potential DoS)
    const subqueryCount = (normalizedQuery.match(/\(\s*SELECT/g) || []).length;
    if (subqueryCount > 5) {
      warnings.push('Query contains many subqueries which may impact performance');
    }

    // Check for missing LIMIT (warning)
    if (!normalizedQuery.includes('LIMIT') && !normalizedQuery.includes('FETCH')) {
      warnings.push('Query has no LIMIT clause - large result sets may impact performance');
    }

    // Check for SELECT *
    if (/SELECT\s+\*/.test(normalizedQuery)) {
      warnings.push('Using SELECT * is not recommended - specify columns explicitly');
    }

    return {
      isValid: errors.length === 0,
      errors,
      warnings,
    };
  }

  /**
   * Sanitize query by removing comments
   */
  sanitize(query: string): string {
    return query
      // Remove single-line comments
      .replace(/--.*$/gm, '')
      // Remove multi-line comments
      .replace(/\/\*[\s\S]*?\*\//g, '')
      // Remove leading/trailing whitespace
      .trim();
  }

  /**
   * Check if a table name is valid
   */
  isValidTableName(tableName: string): boolean {
    // Allow alphanumeric, underscore, and dot (for schema.table)
    return /^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)?$/.test(tableName);
  }

  /**
   * Check if a column name is valid
   */
  isValidColumnName(columnName: string): boolean {
    // Allow alphanumeric and underscore
    return /^[a-zA-Z_][a-zA-Z0-9_]*$/.test(columnName);
  }

  /**
   * Estimate query complexity
   */
  estimateComplexity(query: string): 'low' | 'medium' | 'high' {
    const normalizedQuery = query.toUpperCase();

    let score = 0;

    // Join count
    const joinCount = (normalizedQuery.match(/\bJOIN\b/g) || []).length;
    score += joinCount * 2;

    // Subquery count
    const subqueryCount = (normalizedQuery.match(/\(\s*SELECT/g) || []).length;
    score += subqueryCount * 3;

    // Aggregation functions
    const aggCount = (
      normalizedQuery.match(/\b(COUNT|SUM|AVG|MIN|MAX|GROUP BY)\b/g) || []
    ).length;
    score += aggCount;

    // DISTINCT
    if (normalizedQuery.includes('DISTINCT')) {
      score += 2;
    }

    // ORDER BY
    if (normalizedQuery.includes('ORDER BY')) {
      score += 1;
    }

    // HAVING
    if (normalizedQuery.includes('HAVING')) {
      score += 2;
    }

    if (score <= 3) return 'low';
    if (score <= 8) return 'medium';
    return 'high';
  }
}
