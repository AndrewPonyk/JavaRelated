/**
 * Data Connection Controller
 * Handles HTTP requests for data connection management
 */

import { Request, Response } from 'express';
import { PrismaClient, DataConnectionType, ConnectionStatus } from '@prisma/client';
import { logger } from '../../utils/logger.js';
import { AppError } from '../../utils/errors.js';
import { asyncHandler } from '../../utils/asyncHandler.js';
import { ExtendedCacheService } from '../../services/cache/cacheService.js';
import { connectorFactory, ConnectorType } from '../../services/connectors/index.js';
import { QueryExecutionService } from '../../services/query/QueryExecutionService.js';
import { credentialService } from '../../services/security/credentialService.js';
import {
  CreateDataConnectionSchema,
  UpdateDataConnectionSchema,
  ExecuteQuerySchema,
  PaginationSchema,
  SaveQuerySchema,
} from '../../schemas/dataConnectionSchemas.js';

interface AuthenticatedRequest extends Request {
  user?: {
    id: string;
    email: string;
    role: string;
  };
}

export class DataConnectionController {
  private prisma: PrismaClient;
  private cacheService: ExtendedCacheService;
  private queryExecutionService: QueryExecutionService;

  constructor(
    prisma: PrismaClient,
    cacheService: ExtendedCacheService
  ) {
    this.prisma = prisma;
    this.cacheService = cacheService;
    this.queryExecutionService = new QueryExecutionService(cacheService);
  }

  /**
   * Get all data connections for authenticated user
   * GET /api/data-connections
   */
  getConnections = asyncHandler(async (req: AuthenticatedRequest, res: Response): Promise<void> => {
    const userId = req.user?.id!;
    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const queryParams = PaginationSchema.parse(req.query);
    const { page, limit, sortBy, sortOrder } = queryParams;
    const skip = (page - 1) * limit;

    // Check cache
    const cacheKey = `data_connections:${userId}:${page}:${limit}:${sortBy}:${sortOrder}`;
    const cached = await this.cacheService.get(cacheKey);
    if (cached) {
      res.json(cached);
      return;
    }

    const [connections, total] = await Promise.all([
      this.prisma.dataConnection.findMany({
        where: { userId },
        orderBy: { [sortBy]: sortOrder },
        skip,
        take: limit,
        select: {
          id: true,
          name: true,
          type: true,
          config: true,
          isActive: true,
          lastTested: true,
          status: true,
          createdAt: true,
          updatedAt: true,
        },
      }),
      this.prisma.dataConnection.count({ where: { userId } }),
    ]);

    const result = {
      success: true,
      data: connections,
      pagination: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit),
      },
    };

    await this.cacheService.set(cacheKey, result, 300);
    res.json(result);
  });

  /**
   * Get single data connection
   * GET /api/data-connections/:id
   */
  getConnection = asyncHandler(async (req: AuthenticatedRequest, res: Response): Promise<void> => {
    const userId = req.user?.id!;
    const connectionId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Check cache
    const cacheKey = `data_connection:${connectionId}`;
    const cached = await this.cacheService.get(cacheKey);
    if (cached) {
      res.json(cached);
      return;
    }

    const connection = await this.prisma.dataConnection.findFirst({
      where: { id: connectionId, userId },
      select: {
        id: true,
        name: true,
        type: true,
        config: true,
        isActive: true,
        lastTested: true,
        status: true,
        createdAt: true,
        updatedAt: true,
      },
    });

    if (!connection) {
      throw new AppError('Data connection not found', 404);
    }

    const result = { success: true, data: connection };
    await this.cacheService.set(cacheKey, result, 600);
    res.json(result);
  });

  /**
   * Create new data connection
   * POST /api/data-connections
   */
  createConnection = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const validatedData = CreateDataConnectionSchema.parse(req.body);

    // Check if type is supported
    if (!connectorFactory.isTypeSupported(validatedData.type)) {
      throw new AppError(`Connection type ${validatedData.type} is not yet supported`, 400);
    }

    // Encrypt credentials
    const encryptedCredentials = credentialService.encrypt(validatedData.credentials);

    const connection = await this.prisma.dataConnection.create({
      data: {
        name: validatedData.name,
        type: validatedData.type as DataConnectionType,
        config: validatedData.config,
        credentials: encryptedCredentials as any,
        userId,
        status: ConnectionStatus.PENDING,
      },
    });

    // Invalidate cache
    await this.cacheService.invalidatePattern(`data_connections:${userId}:*`);

    logger.info('Created data connection', { connectionId: connection.id, userId });

    res.status(201).json({
      success: true,
      data: {
        id: connection.id,
        name: connection.name,
        type: connection.type,
        config: connection.config,
        isActive: connection.isActive,
        status: connection.status,
        createdAt: connection.createdAt,
      },
      message: 'Data connection created successfully',
    });
  });

  /**
   * Update data connection
   * PUT /api/data-connections/:id
   */
  updateConnection = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const connectionId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const validatedData = UpdateDataConnectionSchema.parse(req.body);

    // Check ownership
    const existing = await this.prisma.dataConnection.findFirst({
      where: { id: connectionId, userId },
    });

    if (!existing) {
      throw new AppError('Data connection not found', 404);
    }

    // Prepare update data
    const updateData: Record<string, unknown> = {};

    if (validatedData.name) updateData.name = validatedData.name;
    if (validatedData.isActive !== undefined) updateData.isActive = validatedData.isActive;

    if (validatedData.config) {
      // Merge with existing config
      updateData.config = { ...(existing.config as object), ...validatedData.config };
    }

    if (validatedData.credentials) {
      // Get existing credentials, merge, and re-encrypt
      const existingCreds = credentialService.getCredentials(existing.credentials);
      const mergedCreds = { ...existingCreds, ...validatedData.credentials };
      updateData.credentials = credentialService.encrypt(mergedCreds) as unknown as Record<string, unknown>;
      // Reset status since credentials changed
      updateData.status = ConnectionStatus.PENDING;
    }

    const connection = await this.prisma.dataConnection.update({
      where: { id: connectionId },
      data: updateData,
    });

    // Remove from connector pool if credentials changed
    if (validatedData.credentials) {
      await connectorFactory.removeConnector(connectionId);
    }

    // Invalidate caches
    await this.cacheService.delete(`data_connection:${connectionId}`);
    await this.cacheService.invalidatePattern(`data_connections:${userId}:*`);

    logger.info('Updated data connection', { connectionId, userId });

    res.json({
      success: true,
      data: {
        id: connection.id,
        name: connection.name,
        type: connection.type,
        config: connection.config,
        isActive: connection.isActive,
        status: connection.status,
        updatedAt: connection.updatedAt,
      },
      message: 'Data connection updated successfully',
    });
  });

  /**
   * Delete data connection
   * DELETE /api/data-connections/:id
   */
  deleteConnection = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const connectionId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Check ownership
    const existing = await this.prisma.dataConnection.findFirst({
      where: { id: connectionId, userId },
    });

    if (!existing) {
      throw new AppError('Data connection not found', 404);
    }

    // Delete connection
    await this.prisma.dataConnection.delete({
      where: { id: connectionId },
    });

    // Remove from connector pool
    await connectorFactory.removeConnector(connectionId);

    // Invalidate caches
    await this.cacheService.delete(`data_connection:${connectionId}`);
    await this.cacheService.invalidatePattern(`data_connections:${userId}:*`);
    await this.queryExecutionService.invalidateConnectionCache(connectionId);

    logger.info('Deleted data connection', { connectionId, userId });

    res.json({
      success: true,
      message: 'Data connection deleted successfully',
    });
  });

  /**
   * Test data connection
   * POST /api/data-connections/:id/test
   */
  testConnection = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const connectionId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const connection = await this.prisma.dataConnection.findFirst({
      where: { id: connectionId, userId },
    });

    if (!connection) {
      throw new AppError('Data connection not found', 404);
    }

    if (!connectorFactory.isTypeSupported(connection.type)) {
      throw new AppError(`Connection type ${connection.type} is not supported`, 400);
    }

    const config = connection.config as Record<string, unknown>;
    const credentials = credentialService.getCredentials(connection.credentials);

    // Create connector for testing
    const connector = connectorFactory.createConnector(
      connectionId,
      connection.type as ConnectorType,
      {
        host: config.host as string,
        port: config.port as number,
        database: config.database as string,
        ssl: config.ssl as boolean | undefined,
        connectionTimeout: config.connectionTimeout as number | undefined,
      },
      {
        username: credentials.username as string,
        password: credentials.password as string,
      }
    );

    const result = await connector.testConnection();

    // Update connection status
    await this.prisma.dataConnection.update({
      where: { id: connectionId },
      data: {
        status: result.success ? ConnectionStatus.CONNECTED : ConnectionStatus.FAILED,
        lastTested: new Date(),
      },
    });

    // Invalidate cache
    await this.cacheService.delete(`data_connection:${connectionId}`);

    logger.info('Tested data connection', {
      connectionId,
      userId,
      success: result.success,
      latency: result.latency,
    });

    res.json({
      success: true,
      data: result,
    });
  });

  /**
   * Get database schema
   * GET /api/data-connections/:id/schema
   */
  getSchema = asyncHandler(async (req: AuthenticatedRequest, res: Response): Promise<void> => {
    const userId = req.user?.id!;
    const connectionId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Check cache (30 min TTL for schema)
    const cacheKey = `schema:${connectionId}`;
    const cached = await this.cacheService.getSchema(cacheKey);
    if (cached) {
      res.json({ success: true, data: cached, cached: true });
      return;
    }

    const connection = await this.prisma.dataConnection.findFirst({
      where: { id: connectionId, userId },
    });

    if (!connection) {
      throw new AppError('Data connection not found', 404);
    }

    if (!connectorFactory.isTypeSupported(connection.type)) {
      throw new AppError(`Connection type ${connection.type} is not supported`, 400);
    }

    const config = connection.config as Record<string, unknown>;
    const credentials = credentialService.getCredentials(connection.credentials);

    const connector = await connectorFactory.getConnector(
      connectionId,
      connection.type as ConnectorType,
      {
        host: config.host as string,
        port: config.port as number,
        database: config.database as string,
        ssl: config.ssl as boolean | undefined,
      },
      {
        username: credentials.username as string,
        password: credentials.password as string,
      }
    );

    const schemas = await connector.getSchemas();

    // Cache schema
    await this.cacheService.setSchema(cacheKey, schemas, 1800);

    res.json({
      success: true,
      data: schemas,
      cached: false,
    });
  });

  /**
   * Get tables for a connection
   * GET /api/data-connections/:id/tables
   */
  getTables = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const connectionId = req.params.id!;
    const schema = req.query.schema as string | undefined;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const connection = await this.prisma.dataConnection.findFirst({
      where: { id: connectionId, userId },
    });

    if (!connection) {
      throw new AppError('Data connection not found', 404);
    }

    if (!connectorFactory.isTypeSupported(connection.type)) {
      throw new AppError(`Connection type ${connection.type} is not supported`, 400);
    }

    const config = connection.config as Record<string, unknown>;
    const credentials = credentialService.getCredentials(connection.credentials);

    const connector = await connectorFactory.getConnector(
      connectionId,
      connection.type as ConnectorType,
      {
        host: config.host as string,
        port: config.port as number,
        database: config.database as string,
        ssl: config.ssl as boolean | undefined,
      },
      {
        username: credentials.username as string,
        password: credentials.password as string,
      }
    );

    const tables = await connector.getTables(schema);

    res.json({
      success: true,
      data: tables,
    });
  });

  /**
   * Get columns for a table
   * GET /api/data-connections/:id/tables/:table/columns
   */
  getTableColumns = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const connectionId = req.params.id!;
    const tableName = req.params.table!;
    const schema = req.query.schema as string | undefined;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const connection = await this.prisma.dataConnection.findFirst({
      where: { id: connectionId, userId },
    });

    if (!connection) {
      throw new AppError('Data connection not found', 404);
    }

    if (!connectorFactory.isTypeSupported(connection.type)) {
      throw new AppError(`Connection type ${connection.type} is not supported`, 400);
    }

    const config = connection.config as Record<string, unknown>;
    const credentials = credentialService.getCredentials(connection.credentials);

    const connector = await connectorFactory.getConnector(
      connectionId,
      connection.type as ConnectorType,
      {
        host: config.host as string,
        port: config.port as number,
        database: config.database as string,
        ssl: config.ssl as boolean | undefined,
      },
      {
        username: credentials.username as string,
        password: credentials.password as string,
      }
    );

    const columns = await connector.getTableColumns(tableName, schema);

    res.json({
      success: true,
      data: columns,
    });
  });

  /**
   * Execute query
   * POST /api/data-connections/:id/query
   */
  executeQuery = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const connectionId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const validatedData = ExecuteQuerySchema.parse(req.body);

    const connection = await this.prisma.dataConnection.findFirst({
      where: { id: connectionId, userId },
    });

    if (!connection) {
      throw new AppError('Data connection not found', 404);
    }

    if (!connectorFactory.isTypeSupported(connection.type)) {
      throw new AppError(`Connection type ${connection.type} is not supported`, 400);
    }

    const config = connection.config as Record<string, unknown>;
    const credentials = connection.credentials;

    let result;

    if (validatedData.type === 'raw' && validatedData.rawQuery) {
      result = await this.queryExecutionService.executeRaw(
        connectionId,
        connection.type as ConnectorType,
        config,
        credentials as Record<string, unknown>,
        validatedData.rawQuery,
        validatedData.options
      );
    } else if (validatedData.type === 'visual' && validatedData.visualQuery) {
      result = await this.queryExecutionService.executeVisual(
        connectionId,
        connection.type as ConnectorType,
        config,
        credentials as Record<string, unknown>,
        validatedData.visualQuery,
        validatedData.options
      );
    } else {
      throw new AppError('Invalid query type', 400);
    }

    logger.info('Executed query', {
      connectionId,
      userId,
      type: validatedData.type,
      rowCount: result.rowCount,
      executionTime: result.executionTime,
      cached: result.cached,
    });

    res.json({
      success: true,
      data: result,
    });
  });

  /**
   * Save a query
   * POST /api/data-connections/:id/queries
   */
  saveQuery = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const connectionId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const validatedData = SaveQuerySchema.parse(req.body);

    // Verify connection ownership
    const connection = await this.prisma.dataConnection.findFirst({
      where: { id: connectionId, userId },
    });

    if (!connection) {
      throw new AppError('Data connection not found', 404);
    }

    const savedQuery = await this.prisma.savedQuery.create({
      data: {
        name: validatedData.name,
        queryType: validatedData.queryType,
        visualQuery: validatedData.visualQuery as any,
        rawQuery: validatedData.rawQuery || undefined,
        connectionId,
        userId,
      },
    });

    logger.info('Saved query', { queryId: savedQuery.id, connectionId, userId });

    res.status(201).json({
      success: true,
      data: savedQuery,
      message: 'Query saved successfully',
    });
  });

  /**
   * Get saved queries for a connection
   * GET /api/data-connections/:id/queries
   */
  getSavedQueries = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const connectionId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const queries = await this.prisma.savedQuery.findMany({
      where: { connectionId, userId },
      orderBy: { updatedAt: 'desc' },
    });

    res.json({
      success: true,
      data: queries,
    });
  });

  /**
   * Delete a saved query
   * DELETE /api/data-connections/:id/queries/:queryId
   */
  deleteSavedQuery = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const connectionId = req.params.id!;
    const queryId = req.params.queryId;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const query = await this.prisma.savedQuery.findFirst({
      where: { id: queryId, connectionId, userId },
    });

    if (!query) {
      throw new AppError('Query not found', 404);
    }

    await this.prisma.savedQuery.delete({
      where: { id: queryId },
    });

    logger.info('Deleted saved query', { queryId, connectionId, userId });

    res.json({
      success: true,
      message: 'Query deleted successfully',
    });
  });
}

// Factory function
export const createDataConnectionController = (
  prisma: PrismaClient,
  cacheService: ExtendedCacheService
) => {
  return new DataConnectionController(prisma, cacheService);
};
