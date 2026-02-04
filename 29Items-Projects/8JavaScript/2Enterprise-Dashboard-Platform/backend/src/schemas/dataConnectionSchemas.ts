/**
 * Data Connection Zod Schemas
 * Validation schemas for data connection API requests
 */

import { z } from 'zod';

// Database connection type enum
export const DataConnectionTypeSchema = z.enum([
  'MYSQL',
  'POSTGRESQL',
  'MONGODB',
  'REST_API',
  'GRAPHQL_API',
  'CSV_FILE',
  'JSON_FILE',
  'GOOGLE_SHEETS',
  'SALESFORCE',
  'HUBSPOT',
  'SLACK',
  'CUSTOM',
]);

export const ConnectionStatusSchema = z.enum([
  'PENDING',
  'CONNECTED',
  'FAILED',
  'DISABLED',
]);

// Database-specific config schema
export const DatabaseConfigSchema = z.object({
  host: z.string().min(1, 'Host is required'),
  port: z.number().int().min(1).max(65535),
  database: z.string().min(1, 'Database name is required'),
  ssl: z.boolean().optional().default(false),
  connectionTimeout: z.number().int().min(1000).max(60000).optional().default(10000),
  queryTimeout: z.number().int().min(1000).max(300000).optional().default(30000),
});

// Credentials schema
export const CredentialsSchema = z.object({
  username: z.string().min(1, 'Username is required'),
  password: z.string().min(1, 'Password is required'),
});

// Create connection request
export const CreateDataConnectionSchema = z.object({
  name: z.string().min(1, 'Name is required').max(100, 'Name too long'),
  type: DataConnectionTypeSchema,
  config: DatabaseConfigSchema,
  credentials: CredentialsSchema,
});

// Update connection request
export const UpdateDataConnectionSchema = z.object({
  name: z.string().min(1).max(100).optional(),
  config: DatabaseConfigSchema.partial().optional(),
  credentials: CredentialsSchema.partial().optional(),
  isActive: z.boolean().optional(),
});

// Visual query column schema
export const VisualQueryColumnSchema = z.object({
  table: z.string().min(1),
  column: z.string().min(1),
  alias: z.string().optional(),
  aggregation: z.enum(['COUNT', 'SUM', 'AVG', 'MIN', 'MAX', 'COUNT_DISTINCT']).optional(),
});

// Visual query join schema
export const VisualQueryJoinSchema = z.object({
  type: z.enum(['INNER', 'LEFT', 'RIGHT', 'FULL']),
  table: z.string().min(1),
  alias: z.string().optional(),
  on: z.object({
    leftTable: z.string().min(1),
    leftColumn: z.string().min(1),
    rightTable: z.string().min(1),
    rightColumn: z.string().min(1),
  }),
});

// Visual query condition schema
export const VisualQueryConditionSchema = z.object({
  table: z.string().min(1),
  column: z.string().min(1),
  operator: z.enum([
    '=',
    '!=',
    '>',
    '<',
    '>=',
    '<=',
    'LIKE',
    'NOT LIKE',
    'IN',
    'NOT IN',
    'IS NULL',
    'IS NOT NULL',
    'BETWEEN',
  ]),
  value: z.unknown().optional(),
  values: z.array(z.unknown()).optional(),
  logicalOperator: z.enum(['AND', 'OR']).optional(),
});

// Visual query order by schema
export const VisualQueryOrderBySchema = z.object({
  table: z.string().min(1),
  column: z.string().min(1),
  direction: z.enum(['ASC', 'DESC']),
});

// Visual query schema
export const VisualQuerySchema = z.object({
  from: z.object({
    table: z.string().min(1),
    schema: z.string().optional(),
    alias: z.string().optional(),
  }),
  columns: z.array(VisualQueryColumnSchema).min(1, 'At least one column is required'),
  joins: z.array(VisualQueryJoinSchema).optional(),
  where: z.array(VisualQueryConditionSchema).optional(),
  groupBy: z
    .array(
      z.object({
        table: z.string().min(1),
        column: z.string().min(1),
      })
    )
    .optional(),
  having: z.array(VisualQueryConditionSchema).optional(),
  orderBy: z.array(VisualQueryOrderBySchema).optional(),
  limit: z.number().int().min(1).max(10000).optional(),
  offset: z.number().int().min(0).optional(),
});

// Execute query request
export const ExecuteQuerySchema = z.object({
  type: z.enum(['raw', 'visual']),
  rawQuery: z.string().optional(),
  visualQuery: VisualQuerySchema.optional(),
  options: z
    .object({
      timeout: z.number().int().min(1000).max(300000).optional(),
      useCache: z.boolean().optional(),
      cacheTtl: z.number().int().min(1).max(3600).optional(),
      maxRows: z.number().int().min(1).max(10000).optional(),
    })
    .optional(),
}).refine(
  (data) => {
    if (data.type === 'raw' && !data.rawQuery) {
      return false;
    }
    if (data.type === 'visual' && !data.visualQuery) {
      return false;
    }
    return true;
  },
  {
    message: 'rawQuery is required for raw type, visualQuery is required for visual type',
  }
);

// Save query request
export const SaveQuerySchema = z.object({
  name: z.string().min(1, 'Name is required').max(100, 'Name too long'),
  queryType: z.enum(['VISUAL', 'RAW']),
  visualQuery: VisualQuerySchema.optional(),
  rawQuery: z.string().optional(),
}).refine(
  (data) => {
    if (data.queryType === 'RAW' && !data.rawQuery) {
      return false;
    }
    if (data.queryType === 'VISUAL' && !data.visualQuery) {
      return false;
    }
    return true;
  },
  {
    message: 'rawQuery is required for RAW type, visualQuery is required for VISUAL type',
  }
);

// Pagination schema for list queries
export const PaginationSchema = z.object({
  page: z.coerce.number().int().min(1).optional().default(1),
  limit: z.coerce.number().int().min(1).max(100).optional().default(20),
  sortBy: z.string().optional().default('createdAt'),
  sortOrder: z.enum(['asc', 'desc']).optional().default('desc'),
});

// Type exports
export type CreateDataConnectionInput = z.infer<typeof CreateDataConnectionSchema>;
export type UpdateDataConnectionInput = z.infer<typeof UpdateDataConnectionSchema>;
export type ExecuteQueryInput = z.infer<typeof ExecuteQuerySchema>;
export type SaveQueryInput = z.infer<typeof SaveQuerySchema>;
export type PaginationInput = z.infer<typeof PaginationSchema>;
export type VisualQueryInput = z.infer<typeof VisualQuerySchema>;
