/**
 * Data Connection Routes
 * API routes for managing database connections, queries, and schema introspection
 */

import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { createDataConnectionController } from '@/controllers/dataConnection/dataConnectionController.js';
import { prisma } from '@/config/database.js';
import { extendedCacheService } from '@/services/cache/cacheService.js';
import { authenticate } from '@/middleware/auth/authMiddleware.js';
import { csrfProtection } from '@/middleware/security/csrfMiddleware.js';

const router = Router();

// Create controller instance
const dataConnectionController = createDataConnectionController(prisma, extendedCacheService);

// Rate limiting for data connection operations
const dataConnectionRateLimit = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100, // Limit for connection management
  message: {
    success: false,
    error: {
      code: 'RATE_LIMIT_EXCEEDED',
      message: 'Too many data connection requests, please try again later',
      statusCode: 429,
    },
  },
  standardHeaders: true,
  legacyHeaders: false,
});

// Stricter rate limit for query execution
const queryRateLimit = rateLimit({
  windowMs: 1 * 60 * 1000, // 1 minute
  max: 30, // 30 queries per minute
  message: {
    success: false,
    error: {
      code: 'RATE_LIMIT_EXCEEDED',
      message: 'Too many query requests, please try again later',
      statusCode: 429,
    },
  },
  standardHeaders: true,
  legacyHeaders: false,
});

// Apply rate limiting and authentication to all routes
router.use(dataConnectionRateLimit);
router.use(authenticate);

// ============================================================
// Data Connection CRUD Routes
// ============================================================

/**
 * @route   GET /api/data-connections
 * @desc    List all data connections for authenticated user
 * @access  Private
 * @params  ?page=1&limit=20&sortBy=createdAt&sortOrder=desc
 */
router.get('/', dataConnectionController.getConnections);

/**
 * @route   POST /api/data-connections
 * @desc    Create a new data connection
 * @access  Private
 * @body    { name, type, config: { host, port, database, ssl }, credentials: { username, password } }
 */
router.post('/', csrfProtection, dataConnectionController.createConnection);

/**
 * @route   GET /api/data-connections/:id
 * @desc    Get a specific data connection
 * @access  Private (owner only)
 */
router.get('/:id', dataConnectionController.getConnection);

/**
 * @route   PUT /api/data-connections/:id
 * @desc    Update a data connection
 * @access  Private (owner only)
 * @body    { name?, config?, credentials?, isActive? }
 */
router.put('/:id', csrfProtection, dataConnectionController.updateConnection);

/**
 * @route   DELETE /api/data-connections/:id
 * @desc    Delete a data connection
 * @access  Private (owner only)
 */
router.delete('/:id', csrfProtection, dataConnectionController.deleteConnection);

// ============================================================
// Connection Testing & Schema Routes
// ============================================================

/**
 * @route   POST /api/data-connections/:id/test
 * @desc    Test a data connection
 * @access  Private (owner only)
 * @returns { success, latency, version?, error? }
 */
router.post('/:id/test', csrfProtection, dataConnectionController.testConnection);

/**
 * @route   GET /api/data-connections/:id/schema
 * @desc    Get database schema (all schemas with their tables)
 * @access  Private (owner only)
 * @returns { schemas: [{ name, tables: [...] }] }
 */
router.get('/:id/schema', dataConnectionController.getSchema);

/**
 * @route   GET /api/data-connections/:id/tables
 * @desc    List tables in the database
 * @access  Private (owner only)
 * @params  ?schema=public
 * @returns { tables: [{ name, schema, type, rowCount? }] }
 */
router.get('/:id/tables', dataConnectionController.getTables);

/**
 * @route   GET /api/data-connections/:id/tables/:table/columns
 * @desc    Get columns for a specific table
 * @access  Private (owner only)
 * @params  ?schema=public
 * @returns { columns: [{ name, dataType, isNullable, isPrimaryKey, isForeignKey, ... }] }
 */
router.get('/:id/tables/:table/columns', dataConnectionController.getTableColumns);

// ============================================================
// Query Execution Routes
// ============================================================

/**
 * @route   POST /api/data-connections/:id/query
 * @desc    Execute a query against the data connection
 * @access  Private (owner only)
 * @body    { type: 'raw'|'visual', rawQuery?, visualQuery?, options?: { timeout, useCache, cacheTtl, maxRows } }
 * @returns { rows, rowCount, fields, executionTime, cached, validationWarnings? }
 */
router.post('/:id/query', queryRateLimit, csrfProtection, dataConnectionController.executeQuery);

// ============================================================
// Saved Query Routes
// ============================================================

/**
 * @route   GET /api/data-connections/:id/queries
 * @desc    Get saved queries for a connection
 * @access  Private (owner only)
 * @returns { queries: [...] }
 */
router.get('/:id/queries', dataConnectionController.getSavedQueries);

/**
 * @route   POST /api/data-connections/:id/queries
 * @desc    Save a query
 * @access  Private (owner only)
 * @body    { name, queryType: 'VISUAL'|'RAW', visualQuery?, rawQuery? }
 */
router.post('/:id/queries', csrfProtection, dataConnectionController.saveQuery);

/**
 * @route   DELETE /api/data-connections/:id/queries/:queryId
 * @desc    Delete a saved query
 * @access  Private (owner only)
 */
router.delete('/:id/queries/:queryId', csrfProtection, dataConnectionController.deleteSavedQuery);

export default router;
