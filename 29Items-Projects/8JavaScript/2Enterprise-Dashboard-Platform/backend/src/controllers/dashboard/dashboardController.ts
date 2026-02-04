import { Request, Response, NextFunction } from 'express';
import { z } from 'zod';
import { DashboardService } from '../../services/dashboard/dashboardService.js';
import { CacheService, ExtendedCacheService } from '../../services/cache/cacheService.js';
import { logger } from '../../utils/logger.js';
import { AppError } from '../../utils/errors.js';
import { asyncHandler } from '../../utils/asyncHandler.js';

// Validation schemas
const createDashboardSchema = z.object({
  title: z.string().min(1).max(100),
  description: z.string().optional(),
  isPublic: z.boolean().default(false),
  layout: z.array(z.object({
    id: z.string(),
    x: z.number(),
    y: z.number(),
    width: z.number(),
    height: z.number(),
  })),
});

const updateDashboardSchema = z.object({
  title: z.string().min(1).max(100).optional(),
  description: z.string().optional(),
  isPublic: z.boolean().optional(),
  layout: z.array(z.object({
    id: z.string(),
    x: z.number(),
    y: z.number(),
    width: z.number(),
    height: z.number(),
  })).optional(),
});

const dashboardQuerySchema = z.object({
  page: z.string().optional().transform(val => val ? parseInt(val) : 1),
  limit: z.string().optional().transform(val => val ? parseInt(val) : 10),
  search: z.string().optional(),
  sortBy: z.enum(['createdAt', 'updatedAt', 'title']).optional().default('updatedAt'),
  sortOrder: z.enum(['asc', 'desc']).optional().default('desc'),
});

interface AuthenticatedRequest extends Request {
  user?: {
    id: string;
    email: string;
    role: string;
  };
}

export class DashboardController {
  private dashboardService: DashboardService;
  private cacheService: ExtendedCacheService;

  constructor(dashboardService: DashboardService, cacheService: ExtendedCacheService) {
    this.dashboardService = dashboardService;
    this.cacheService = cacheService;
  }

  /**
   * Get all dashboards for authenticated user
   * GET /api/dashboards
   */
  getDashboards = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Validate query parameters
    const queryParams = dashboardQuerySchema.parse(req.query);
    const { page, limit, search, sortBy, sortOrder } = queryParams;

    // Check cache first
    const cacheKey = `dashboards:${userId}:${page}:${limit}:${search}:${sortBy}:${sortOrder}`;
    const cachedData = await this.cacheService.get(cacheKey);

    if (cachedData) {
      logger.info('Returning cached dashboard data', { userId, cacheKey });
      return res.json(cachedData);
    }

    try {
      // Fetch dashboards from service
      const dashboards = await this.dashboardService.getUserDashboards(userId, {
        page,
        limit,
        search,
        sortBy,
        sortOrder,
      });

      // Cache the result
      await this.cacheService.set(cacheKey, dashboards, 300); // 5 minutes

      logger.info('Retrieved dashboards for user', {
        userId,
        count: dashboards.data.length,
        total: dashboards.total,
      });

      return res.json(dashboards);
    } catch (error) {
      logger.error('Failed to get dashboards', { userId, error });
      throw new AppError('Failed to retrieve dashboards', 500);
    }
  });

  /**
   * Get single dashboard by ID
   * GET /api/dashboards/:id
   */
  getDashboard = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Check cache first
    const cacheKey = `dashboard:${dashboardId}:${userId}`;
    const cachedDashboard = await this.cacheService.get(cacheKey);

    if (cachedDashboard) {
      logger.info('Returning cached dashboard', { dashboardId, userId });
      return res.json(cachedDashboard);
    }

    try {
      const dashboard = await this.dashboardService.getDashboard(dashboardId, userId!);

      if (!dashboard) {
        throw new AppError('Dashboard not found', 404);
      }

      // Cache the dashboard
      await this.cacheService.set(cacheKey, dashboard, 600); // 10 minutes

      logger.info('Retrieved dashboard', { dashboardId, userId });
      return res.json(dashboard);
    } catch (error) {
      if (error instanceof AppError) throw error;
      logger.error('Failed to get dashboard', { dashboardId, userId, error });
      throw new AppError('Failed to retrieve dashboard', 500);
    }
  });

  /**
   * Create new dashboard
   * POST /api/dashboards
   */
  createDashboard = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Validate request body
    const validatedData = createDashboardSchema.parse(req.body);

    try {
      const dashboard = await this.dashboardService.createDashboard({
        ...validatedData,
        userId,
      });

      // Invalidate user's dashboard cache
      await this.cacheService.invalidatePattern(`dashboards:${userId}:*`);

      logger.info('Created new dashboard', { dashboardId: dashboard.id, userId });

      res.status(201).json({
        success: true,
        data: dashboard,
        message: 'Dashboard created successfully',
      });
    } catch (error) {
      logger.error('Failed to create dashboard', { userId, error });
      throw new AppError('Failed to create dashboard', 500);
    }
  });

  /**
   * Update existing dashboard
   * PUT /api/dashboards/:id
   */
  updateDashboard = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Validate request body
    const validatedData = updateDashboardSchema.parse(req.body);

    try {
      const dashboard = await this.dashboardService.updateDashboard(
        dashboardId,
        userId!,
        validatedData
      );

      if (!dashboard) {
        throw new AppError('Dashboard not found or access denied', 404);
      }

      // Invalidate relevant caches
      await this.cacheService.delete(`dashboard:${dashboardId}:${userId}`);
      await this.cacheService.invalidatePattern(`dashboards:${userId}:*`);

      logger.info('Updated dashboard', { dashboardId, userId });

      res.json({
        success: true,
        data: dashboard,
        message: 'Dashboard updated successfully',
      });
    } catch (error) {
      if (error instanceof AppError) throw error;
      logger.error('Failed to update dashboard', { dashboardId, userId, error });
      throw new AppError('Failed to update dashboard', 500);
    }
  });

  /**
   * Delete dashboard
   * DELETE /api/dashboards/:id
   */
  deleteDashboard = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    try {
      const deleted = await this.dashboardService.deleteDashboard(dashboardId, userId!);

      if (!deleted) {
        throw new AppError('Dashboard not found or access denied', 404);
      }

      // Clean up caches
      await this.cacheService.delete(`dashboard:${dashboardId}:${userId}`);
      await this.cacheService.invalidatePattern(`dashboards:${userId}:*`);

      logger.info('Deleted dashboard', { dashboardId, userId });

      res.json({
        success: true,
        message: 'Dashboard deleted successfully',
      });
    } catch (error) {
      if (error instanceof AppError) throw error;
      logger.error('Failed to delete dashboard', { dashboardId, userId, error });
      throw new AppError('Failed to delete dashboard', 500);
    }
  });

  /**
   * Share dashboard
   * POST /api/dashboards/:id/share
   */
  shareDashboard = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const { emails, permission } = req.body;

    // Validate input
    const shareSchema = z.object({
      emails: z.array(z.string().email()),
      permission: z.enum(['read', 'write']).default('read').transform(p => p.toUpperCase() as 'READ' | 'WRITE'),
    });

    const validatedData = shareSchema.parse({ emails, permission });

    try {
      const shareResult = await this.dashboardService.shareDashboard(
        dashboardId,
        userId!,
        validatedData.emails,
        validatedData.permission as any
      );

      logger.info('Shared dashboard', {
        dashboardId,
        userId,
        sharedWith: validatedData.emails,
        permission: validatedData.permission,
      });

      res.json({
        success: true,
        data: shareResult,
        message: 'Dashboard shared successfully',
      });
    } catch (error) {
      logger.error('Failed to share dashboard', { dashboardId, userId, error });
      throw new AppError('Failed to share dashboard', 500);
    }
  });

  /**
   * Get dashboard analytics data
   * GET /api/dashboards/:id/analytics
   */
  getDashboardAnalytics = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;
    const { startDate, endDate, granularity = 'day' } = req.query;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Validate date parameters
    const analyticsSchema = z.object({
      startDate: z.string().optional(),
      endDate: z.string().optional(),
      granularity: z.enum(['hour', 'day', 'week', 'month']).default('day'),
    });

    const validatedParams = analyticsSchema.parse({
      startDate,
      endDate,
      granularity,
    });

    // Check cache
    const cacheKey = `analytics:${dashboardId}:${validatedParams.startDate}:${validatedParams.endDate}:${validatedParams.granularity}`;
    const cachedAnalytics = await this.cacheService.get(cacheKey);

    if (cachedAnalytics) {
      return res.json(cachedAnalytics);
    }

    try {
      const analytics = await this.dashboardService.getDashboardAnalytics(
        dashboardId,
        userId!,
        validatedParams
      );

      // Cache analytics data for 15 minutes
      await this.cacheService.set(cacheKey, analytics, 900);

      return res.json(analytics);
    } catch (error) {
      logger.error('Failed to get dashboard analytics', { dashboardId, userId, error });
      throw new AppError('Failed to retrieve analytics data', 500);
    }
  });
}

// Export controller instance factory
export const createDashboardController = (
  dashboardService: DashboardService,
  cacheService: ExtendedCacheService
) => {
  return new DashboardController(dashboardService, cacheService);
};

// Export validation schemas for reuse
export {
  createDashboardSchema,
  updateDashboardSchema,
  dashboardQuerySchema,
};