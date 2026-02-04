import { Router, Request, Response } from 'express';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import { prisma } from '@/config/database.js';
import { extendedCacheService as cacheService } from '@/services/cache/cacheService.js';
import { authenticate, optionalAuthenticate } from '@/middleware/auth/authMiddleware.js';
import { csrfProtection } from '@/middleware/security/csrfMiddleware.js';
import { asyncHandler } from '@/utils/asyncHandler.js';
import { logger, logBusinessEvent } from '@/utils/logger.js';
import { NotFoundError, ValidationError, AuthorizationError } from '@/utils/errors.js';
import crypto from 'crypto';

const router = Router();

// Request with user
interface AuthenticatedRequest extends Request {
  user?: {
    id: string;
    email: string;
    role: string;
  };
}

// Rate limiting
const widgetRateLimit = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 200,
  message: {
    success: false,
    error: {
      code: 'RATE_LIMIT_EXCEEDED',
      message: 'Too many widget requests, please try again later',
      statusCode: 429,
    },
  },
  standardHeaders: true,
  legacyHeaders: false,
});

router.use(widgetRateLimit);

// Validation schemas
const createWidgetSchema = z.object({
  title: z.string().min(1).max(100),
  description: z.string().max(500).optional(),
  type: z.enum([
    'CHART_LINE', 'CHART_BAR', 'CHART_PIE', 'CHART_AREA', 'CHART_SCATTER',
    'TABLE', 'METRIC', 'TEXT', 'IMAGE', 'MAP', 'HEATMAP', 'GAUGE', 'CUSTOM'
  ]),
  config: z.record(z.any()).default({}),
  position: z.object({
    x: z.number().default(0),
    y: z.number().default(0),
    width: z.number().default(4),
    height: z.number().default(3)
  }).default({}),
  dashboardId: z.string(),
  dataSource: z.string().optional(),
  query: z.string().optional(),
  refreshRate: z.number().min(0).optional()
});

const updateWidgetSchema = z.object({
  title: z.string().min(1).max(100).optional(),
  description: z.string().max(500).optional(),
  type: z.enum([
    'CHART_LINE', 'CHART_BAR', 'CHART_PIE', 'CHART_AREA', 'CHART_SCATTER',
    'TABLE', 'METRIC', 'TEXT', 'IMAGE', 'MAP', 'HEATMAP', 'GAUGE', 'CUSTOM'
  ]).optional(),
  config: z.record(z.any()).optional(),
  position: z.object({
    x: z.number(),
    y: z.number(),
    width: z.number(),
    height: z.number()
  }).optional(),
  dataSource: z.string().optional(),
  query: z.string().optional(),
  refreshRate: z.number().min(0).optional()
});

const paginationSchema = z.object({
  page: z.string().optional().transform(val => val ? parseInt(val) : 1),
  limit: z.string().optional().transform(val => val ? parseInt(val) : 20),
  search: z.string().optional(),
  type: z.string().optional(),
  sortBy: z.enum(['createdAt', 'updatedAt', 'title']).optional().default('updatedAt'),
  sortOrder: z.enum(['asc', 'desc']).optional().default('desc')
});

// Public routes (templates)
/**
 * @route   GET /api/widgets/templates
 * @desc    Get widget templates
 * @access  Public (optional auth)
 */
router.get('/templates', optionalAuthenticate, asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const { page, limit, search, type } = paginationSchema.parse(req.query);

  const where: any = {
    dashboard: {
      isTemplate: true,
      isPublic: true
    }
  };

  if (search) {
    where.OR = [
      { title: { contains: search, mode: 'insensitive' } },
      { description: { contains: search, mode: 'insensitive' } }
    ];
  }

  if (type) {
    where.type = type;
  }

  const skip = (page - 1) * limit;

  const [widgets, total] = await Promise.all([
    prisma.widget.findMany({
      where,
      skip,
      take: limit,
      orderBy: { createdAt: 'desc' },
      include: {
        user: {
          select: { id: true, firstName: true, lastName: true }
        },
        dashboard: {
          select: { id: true, title: true }
        }
      }
    }),
    prisma.widget.count({ where })
  ]);

  const templates = widgets.map(w => ({
    id: w.id,
    title: w.title,
    description: w.description,
    type: w.type,
    config: w.config,
    author: w.user ? `${w.user.firstName || ''} ${w.user.lastName || ''}`.trim() || 'Anonymous' : 'Anonymous',
    dashboard: w.dashboard?.title,
    createdAt: w.createdAt,
    downloads: Math.floor(Math.random() * 1000) // Placeholder - would need separate tracking
  }));

  res.json({
    success: true,
    data: templates,
    meta: {
      total,
      page,
      limit,
      pages: Math.ceil(total / limit)
    },
    message: 'Widget templates retrieved successfully'
  });
}));

// Apply authentication
router.use(authenticate);

/**
 * @route   GET /api/widgets
 * @desc    Get user's widgets
 * @access  Private
 */
router.get('/', asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  if (!userId) {
    throw new AuthorizationError('User not authenticated');
  }

  const { page, limit, search, type, sortBy, sortOrder } = paginationSchema.parse(req.query);

  const where: any = { userId };

  if (search) {
    where.OR = [
      { title: { contains: search, mode: 'insensitive' } },
      { description: { contains: search, mode: 'insensitive' } }
    ];
  }

  if (type) {
    where.type = type;
  }

  const skip = (page - 1) * limit;

  const [widgets, total] = await Promise.all([
    prisma.widget.findMany({
      where,
      skip,
      take: limit,
      orderBy: { [sortBy]: sortOrder },
      include: {
        dashboard: {
          select: { id: true, title: true, isPublic: true }
        }
      }
    }),
    prisma.widget.count({ where })
  ]);

  const pages = Math.ceil(total / limit);

  logger.info('Retrieved user widgets', { userId, count: widgets.length, total });

  res.json({
    success: true,
    data: widgets,
    meta: {
      total,
      page,
      limit,
      pages,
      hasNext: page < pages,
      hasPrev: page > 1
    },
    message: 'Widgets retrieved successfully'
  });
}));

/**
 * @route   GET /api/widgets/shared
 * @desc    Get widgets shared with user
 * @access  Private
 */
router.get('/shared', asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  if (!userId) {
    throw new AuthorizationError('User not authenticated');
  }

  const { page, limit, search, type } = paginationSchema.parse(req.query);

  // Get dashboards shared with user
  const sharedDashboards = await prisma.dashboardShare.findMany({
    where: { userId },
    select: { dashboardId: true, permission: true }
  });

  const dashboardIds = sharedDashboards.map(s => s.dashboardId);

  const where: any = {
    dashboardId: { in: dashboardIds },
    userId: { not: userId } // Exclude own widgets
  };

  if (search) {
    where.OR = [
      { title: { contains: search, mode: 'insensitive' } },
      { description: { contains: search, mode: 'insensitive' } }
    ];
  }

  if (type) {
    where.type = type;
  }

  const skip = (page - 1) * limit;

  const [widgets, total] = await Promise.all([
    prisma.widget.findMany({
      where,
      skip,
      take: limit,
      orderBy: { updatedAt: 'desc' },
      include: {
        user: {
          select: { id: true, firstName: true, lastName: true, email: true }
        },
        dashboard: {
          select: { id: true, title: true }
        }
      }
    }),
    prisma.widget.count({ where })
  ]);

  const sharedWidgets = widgets.map(w => {
    const share = sharedDashboards.find(s => s.dashboardId === w.dashboardId);
    return {
      ...w,
      sharedBy: w.user ? `${w.user.firstName || ''} ${w.user.lastName || ''}`.trim() || w.user.email : 'Unknown',
      permission: share?.permission || 'READ'
    };
  });

  res.json({
    success: true,
    data: sharedWidgets,
    meta: {
      total,
      page,
      limit,
      pages: Math.ceil(total / limit)
    },
    message: 'Shared widgets retrieved successfully'
  });
}));

/**
 * @route   GET /api/widgets/:id
 * @desc    Get widget by ID
 * @access  Private
 */
router.get('/:id', asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  const widgetId = req.params.id!;

  if (!userId) {
    throw new AuthorizationError('User not authenticated');
  }

  // Check cache
  const cached = await cacheService.getWidget(widgetId);
  if (cached) {
    return res.json({
      success: true,
      data: cached,
      message: 'Widget retrieved successfully'
    });
  }

  const widget = await prisma.widget.findFirst({
    where: {
      id: widgetId,
      OR: [
        { userId },
        { dashboard: { isPublic: true } },
        { dashboard: { dashboardShares: { some: { userId } } } }
      ]
    },
    include: {
      dashboard: {
        select: { id: true, title: true, userId: true }
      },
      user: {
        select: { id: true, firstName: true, lastName: true }
      }
    }
  });

  if (!widget) {
    throw new NotFoundError('Widget');
  }

  // Cache the widget
  await cacheService.setWidget(widgetId, widget);

  return res.json({
    success: true,
    data: widget,
    message: 'Widget retrieved successfully'
  });
}));

/**
 * @route   POST /api/widgets
 * @desc    Create a new widget
 * @access  Private
 */
router.post('/', csrfProtection, asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  if (!userId) {
    throw new AuthorizationError('User not authenticated');
  }

  const data = createWidgetSchema.parse(req.body);

  // Verify user has access to the dashboard
  const dashboard = await prisma.dashboard.findFirst({
    where: {
      id: data.dashboardId,
      OR: [
        { userId },
        { dashboardShares: { some: { userId, permission: { in: ['WRITE', 'ADMIN'] } } } }
      ]
    }
  });

  if (!dashboard) {
    throw new NotFoundError('Dashboard');
  }

  const widget = await prisma.widget.create({
    data: {
      id: crypto.randomUUID(),
      title: data.title,
      description: data.description,
      type: data.type,
      config: data.config || {},
      position: data.position || {},
      dataSource: data.dataSource,
      query: data.query,
      refreshRate: data.refreshRate,
      dashboardId: data.dashboardId,
      userId
    },
    include: {
      dashboard: {
        select: { id: true, title: true }
      }
    }
  });

  // Invalidate dashboard cache
  await cacheService.invalidateDashboard(data.dashboardId);

  // Log activity
  await prisma.activityLog.create({
    data: {
      action: 'WIDGET_CREATED',
      entity: 'widget',
      entityId: widget.id,
      dashboardId: data.dashboardId,
      userId,
      details: JSON.stringify({ title: widget.title, type: widget.type })
    }
  });

  logBusinessEvent('WIDGET_CREATED', {
    widgetId: widget.id,
    dashboardId: data.dashboardId,
    userId,
    type: widget.type
  });

  logger.info('Widget created', { widgetId: widget.id, dashboardId: data.dashboardId, userId });

  res.status(201).json({
    success: true,
    data: widget,
    message: 'Widget created successfully'
  });
}));

/**
 * @route   PUT /api/widgets/:id
 * @desc    Update widget
 * @access  Private
 */
router.put('/:id', csrfProtection, asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  const widgetId = req.params.id!;

  if (!userId) {
    throw new AuthorizationError('User not authenticated');
  }

  const data = updateWidgetSchema.parse(req.body);

  // Verify user has write access
  const existingWidget = await prisma.widget.findFirst({
    where: {
      id: widgetId,
      OR: [
        { userId },
        { dashboard: { dashboardShares: { some: { userId, permission: { in: ['WRITE', 'ADMIN'] } } } } }
      ]
    }
  });

  if (!existingWidget) {
    throw new NotFoundError('Widget');
  }

  const widget = await prisma.widget.update({
    where: { id: widgetId },
    data: {
      ...(data.title && { title: data.title }),
      ...(data.description !== undefined && { description: data.description }),
      ...(data.type && { type: data.type }),
      ...(data.config && { config: data.config }),
      ...(data.position && { position: data.position }),
      ...(data.dataSource !== undefined && { dataSource: data.dataSource }),
      ...(data.query !== undefined && { query: data.query }),
      ...(data.refreshRate !== undefined && { refreshRate: data.refreshRate })
    },
    include: {
      dashboard: {
        select: { id: true, title: true }
      }
    }
  });

  // Invalidate caches
  await Promise.all([
    cacheService.invalidateWidget(widgetId),
    cacheService.invalidateDashboard(existingWidget.dashboardId)
  ]);

  logger.info('Widget updated', { widgetId, userId });

  res.json({
    success: true,
    data: widget,
    message: 'Widget updated successfully'
  });
}));

/**
 * @route   DELETE /api/widgets/:id
 * @desc    Delete widget
 * @access  Private
 */
router.delete('/:id', csrfProtection, asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  const widgetId = req.params.id!;

  if (!userId) {
    throw new AuthorizationError('User not authenticated');
  }

  // Verify user has admin access
  const widget = await prisma.widget.findFirst({
    where: {
      id: widgetId,
      OR: [
        { userId },
        { dashboard: { userId } },
        { dashboard: { dashboardShares: { some: { userId, permission: 'ADMIN' } } } }
      ]
    }
  });

  if (!widget) {
    throw new NotFoundError('Widget');
  }

  await prisma.widget.delete({
    where: { id: widgetId }
  });

  // Invalidate caches
  await Promise.all([
    cacheService.invalidateWidget(widgetId),
    cacheService.invalidateDashboard(widget.dashboardId)
  ]);

  // Log activity
  await prisma.activityLog.create({
    data: {
      action: 'WIDGET_DELETED',
      entity: 'widget',
      entityId: widgetId,
      dashboardId: widget.dashboardId,
      userId,
      details: JSON.stringify({ title: widget.title })
    }
  });

  logger.info('Widget deleted', { widgetId, userId });

  res.json({
    success: true,
    message: 'Widget deleted successfully'
  });
}));

/**
 * @route   POST /api/widgets/:id/duplicate
 * @desc    Duplicate a widget
 * @access  Private
 */
router.post('/:id/duplicate', csrfProtection, asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  const widgetId = req.params.id!;
  const { targetDashboardId } = req.body;

  if (!userId) {
    throw new AuthorizationError('User not authenticated');
  }

  // Get original widget
  const original = await prisma.widget.findFirst({
    where: {
      id: widgetId,
      OR: [
        { userId },
        { dashboard: { isPublic: true } },
        { dashboard: { dashboardShares: { some: { userId } } } }
      ]
    }
  });

  if (!original) {
    throw new NotFoundError('Widget');
  }

  // Verify target dashboard access
  const dashboardId = targetDashboardId || original.dashboardId;
  const targetDashboard = await prisma.dashboard.findFirst({
    where: {
      id: dashboardId,
      OR: [
        { userId },
        { dashboardShares: { some: { userId, permission: { in: ['WRITE', 'ADMIN'] } } } }
      ]
    }
  });

  if (!targetDashboard) {
    throw new NotFoundError('Target dashboard');
  }

  // Create duplicate
  const duplicate = await prisma.widget.create({
    data: {
      id: crypto.randomUUID(),
      title: `Copy of ${original.title}`,
      description: original.description,
      type: original.type,
      config: original.config || {},
      position: original.position || {},
      dataSource: original.dataSource,
      query: original.query,
      refreshRate: original.refreshRate,
      dashboardId,
      userId
    },
    include: {
      dashboard: {
        select: { id: true, title: true }
      }
    }
  });

  // Invalidate dashboard cache
  await cacheService.invalidateDashboard(dashboardId);

  logger.info('Widget duplicated', { originalId: widgetId, newId: duplicate.id, userId });

  res.status(201).json({
    success: true,
    data: duplicate,
    message: 'Widget duplicated successfully'
  });
}));

/**
 * @route   POST /api/widgets/:id/refresh
 * @desc    Refresh widget data
 * @access  Private
 */
router.post('/:id/refresh', asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  const widgetId = req.params.id!;

  if (!userId) {
    throw new AuthorizationError('User not authenticated');
  }

  const widget = await prisma.widget.findFirst({
    where: {
      id: widgetId,
      OR: [
        { userId },
        { dashboard: { isPublic: true } },
        { dashboard: { dashboardShares: { some: { userId } } } }
      ]
    }
  });

  if (!widget) {
    throw new NotFoundError('Widget');
  }

  // Invalidate cached widget data
  await cacheService.invalidateWidget(widgetId);

  // Update last fetch timestamp
  const updated = await prisma.widget.update({
    where: { id: widgetId },
    data: { lastFetch: new Date() }
  });

  res.json({
    success: true,
    data: { lastFetch: updated.lastFetch },
    message: 'Widget data refreshed successfully'
  });
}));

export default router;
