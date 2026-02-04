import { Router, Request, Response } from 'express';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import { prisma } from '@/config/database.js';
import { extendedCacheService as cacheService } from '@/services/cache/cacheService.js';
import { authenticate } from '@/middleware/auth/authMiddleware.js';
import { asyncHandler } from '@/utils/asyncHandler.js';
import { logger } from '@/utils/logger.js';
import { AuthorizationError } from '@/utils/errors.js';

const router = Router();

// Request with user
interface AuthenticatedRequest extends Request {
  user?: {
    id: string;
    email: string;
    role: string;
  };
}

// Rate limiting for search
const searchRateLimit = rateLimit({
  windowMs: 1 * 60 * 1000, // 1 minute
  max: 60, // 60 requests per minute
  message: {
    success: false,
    error: {
      code: 'RATE_LIMIT_EXCEEDED',
      message: 'Too many search requests, please slow down',
      statusCode: 429,
    },
  },
  standardHeaders: true,
  legacyHeaders: false,
});

router.use(searchRateLimit);
router.use(authenticate);

// Search validation schema
const searchSchema = z.object({
  q: z.string().min(1).max(100),
  type: z.enum(['all', 'dashboards', 'widgets', 'users', 'pages']).optional().default('all'),
  limit: z.string().optional().transform(val => val ? Math.min(parseInt(val), 50) : 10)
});

// Search result types
interface SearchResult {
  id: string;
  type: 'dashboard' | 'widget' | 'user' | 'page';
  title: string;
  description?: string;
  url: string;
  icon?: string;
  metadata?: Record<string, any>;
}

// Static pages for search
const staticPages = [
  { id: 'dashboard', title: 'Dashboard Overview', description: 'Main dashboard with analytics', url: '/dashboard', icon: 'LayoutDashboard' },
  { id: 'analytics-reports', title: 'Analytics Reports', description: 'View and generate reports', url: '/analytics/reports', icon: 'FileText' },
  { id: 'analytics-insights', title: 'AI Insights', description: 'AI-powered analytics insights', url: '/analytics/insights', icon: 'Lightbulb' },
  { id: 'analytics-ml', title: 'ML Analytics', description: 'Machine learning analytics', url: '/analytics/ml', icon: 'Brain' },
  { id: 'analytics-financial', title: 'Financial Analytics', description: 'Financial data and trends', url: '/analytics/financial', icon: 'DollarSign' },
  { id: 'data-sources', title: 'Data Sources', description: 'Manage data connections', url: '/data-sources', icon: 'Database' },
  { id: 'widgets-my', title: 'My Widgets', description: 'Your created widgets', url: '/widgets/my', icon: 'LayoutGrid' },
  { id: 'widgets-shared', title: 'Shared Widgets', description: 'Widgets shared with you', url: '/widgets/shared', icon: 'Share2' },
  { id: 'widgets-templates', title: 'Widget Templates', description: 'Widget template gallery', url: '/widgets/templates', icon: 'Package' },
  { id: 'calendar', title: 'Calendar', description: 'Events and schedule', url: '/calendar', icon: 'Calendar' },
  { id: 'notifications', title: 'Notifications', description: 'View all notifications', url: '/notifications', icon: 'Bell' },
  { id: 'settings', title: 'Settings', description: 'User preferences', url: '/settings', icon: 'Settings' },
  { id: 'profile', title: 'Profile', description: 'Your profile page', url: '/profile', icon: 'User' },
  { id: 'admin-users', title: 'User Management', description: 'Manage users (Admin)', url: '/admin/users', icon: 'Users' },
  { id: 'admin-settings', title: 'System Settings', description: 'System configuration (Admin)', url: '/admin/settings', icon: 'Cog' },
  { id: 'admin-security', title: 'Security', description: 'Security settings (Admin)', url: '/admin/security', icon: 'Shield' },
  { id: 'admin-audit', title: 'Audit Logs', description: 'View audit logs (Admin)', url: '/admin/audit', icon: 'FileSearch' },
  { id: 'support', title: 'Support', description: 'Help and support', url: '/support', icon: 'HelpCircle' },
];

/**
 * @route   GET /api/search
 * @desc    Global search across dashboards, widgets, users, and pages
 * @access  Private
 */
router.get('/', asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  if (!userId) {
    throw new AuthorizationError('User not authenticated');
  }

  const { q, type, limit } = searchSchema.parse(req.query);
  const query = q.toLowerCase().trim();

  // Check cache
  const cacheKey = `search:${userId}:${query}:${type}:${limit}`;
  const cached = await cacheService.get<SearchResult[]>(cacheKey);
  if (cached) {
    return res.json({
      success: true,
      data: cached,
      message: 'Search results retrieved successfully'
    });
  }

  const results: SearchResult[] = [];
  const limitPerType = Math.ceil(limit / 4);

  // Search dashboards
  if (type === 'all' || type === 'dashboards') {
    const dashboards = await prisma.dashboard.findMany({
      where: {
        OR: [
          { title: { contains: query, mode: 'insensitive' } },
          { description: { contains: query, mode: 'insensitive' } }
        ],
        AND: [
          {
            OR: [
              { userId },
              { isPublic: true },
              { dashboardShares: { some: { userId } } }
            ]
          }
        ]
      },
      select: {
        id: true,
        title: true,
        description: true,
        isPublic: true,
        updatedAt: true
      },
      take: type === 'dashboards' ? limit : limitPerType,
      orderBy: { updatedAt: 'desc' }
    });

    results.push(...dashboards.map(d => ({
      id: d.id,
      type: 'dashboard' as const,
      title: d.title,
      description: d.description || undefined,
      url: `/dashboard/${d.id}`,
      icon: 'LayoutDashboard',
      metadata: {
        isPublic: d.isPublic,
        updatedAt: d.updatedAt
      }
    })));
  }

  // Search widgets
  if (type === 'all' || type === 'widgets') {
    const widgets = await prisma.widget.findMany({
      where: {
        OR: [
          { title: { contains: query, mode: 'insensitive' } },
          { description: { contains: query, mode: 'insensitive' } }
        ],
        AND: [
          {
            OR: [
              { userId },
              { dashboard: { isPublic: true } },
              { dashboard: { dashboardShares: { some: { userId } } } }
            ]
          }
        ]
      },
      select: {
        id: true,
        title: true,
        description: true,
        type: true,
        dashboardId: true,
        dashboard: {
          select: { title: true }
        }
      },
      take: type === 'widgets' ? limit : limitPerType,
      orderBy: { updatedAt: 'desc' }
    });

    results.push(...widgets.map(w => ({
      id: w.id,
      type: 'widget' as const,
      title: w.title,
      description: w.description || `${w.type} widget in ${w.dashboard?.title}`,
      url: `/dashboard/${w.dashboardId}?widget=${w.id}`,
      icon: getWidgetIcon(w.type),
      metadata: {
        widgetType: w.type,
        dashboard: w.dashboard?.title
      }
    })));
  }

  // Search users (only if current user is admin)
  if (type === 'all' || type === 'users') {
    const currentUser = await prisma.user.findUnique({
      where: { id: userId },
      select: { role: true }
    });

    if (currentUser && ['SUPER_ADMIN', 'ADMIN', 'MANAGER'].includes(currentUser.role)) {
      const users = await prisma.user.findMany({
        where: {
          OR: [
            { email: { contains: query, mode: 'insensitive' } },
            { firstName: { contains: query, mode: 'insensitive' } },
            { lastName: { contains: query, mode: 'insensitive' } }
          ],
          isActive: true
        },
        select: {
          id: true,
          email: true,
          firstName: true,
          lastName: true,
          role: true
        },
        take: type === 'users' ? limit : limitPerType
      });

      results.push(...users.map(u => ({
        id: u.id,
        type: 'user' as const,
        title: `${u.firstName || ''} ${u.lastName || ''}`.trim() || u.email,
        description: u.email,
        url: `/admin/users?user=${u.id}`,
        icon: 'User',
        metadata: {
          role: u.role
        }
      })));
    }
  }

  // Search pages
  if (type === 'all' || type === 'pages') {
    const matchingPages = staticPages.filter(page =>
      page.title.toLowerCase().includes(query) ||
      page.description.toLowerCase().includes(query)
    ).slice(0, type === 'pages' ? limit : limitPerType);

    results.push(...matchingPages.map(p => ({
      id: p.id,
      type: 'page' as const,
      title: p.title,
      description: p.description,
      url: p.url,
      icon: p.icon
    })));
  }

  // Sort results by relevance (exact matches first, then partial matches)
  results.sort((a, b) => {
    const aExact = a.title.toLowerCase() === query;
    const bExact = b.title.toLowerCase() === query;
    if (aExact && !bExact) return -1;
    if (!aExact && bExact) return 1;

    const aStartsWith = a.title.toLowerCase().startsWith(query);
    const bStartsWith = b.title.toLowerCase().startsWith(query);
    if (aStartsWith && !bStartsWith) return -1;
    if (!aStartsWith && bStartsWith) return 1;

    return 0;
  });

  // Limit total results
  const limitedResults = results.slice(0, limit);

  // Cache results for 5 minutes
  await cacheService.set(cacheKey, limitedResults, 300);

  logger.info('Search performed', { userId, query, type, resultCount: limitedResults.length });

  return res.json({
    success: true,
    data: limitedResults,
    meta: {
      query: q,
      type,
      total: limitedResults.length
    },
    message: 'Search results retrieved successfully'
  });
}));

/**
 * @route   GET /api/search/suggestions
 * @desc    Get search suggestions based on partial query
 * @access  Private
 */
router.get('/suggestions', asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  if (!userId) {
    throw new AuthorizationError('User not authenticated');
  }

  const q = (req.query.q as string || '').trim();
  if (q.length < 2) {
    return res.json({
      success: true,
      data: [],
      message: 'Query too short for suggestions'
    });
  }

  const query = q.toLowerCase();

  // Get suggestions from recent user activity and common items
  const suggestions: string[] = [];

  // Dashboard titles
  const dashboards = await prisma.dashboard.findMany({
    where: {
      title: { contains: query, mode: 'insensitive' },
      OR: [
        { userId },
        { isPublic: true }
      ]
    },
    select: { title: true },
    take: 3
  });
  suggestions.push(...dashboards.map(d => d.title));

  // Widget titles
  const widgets = await prisma.widget.findMany({
    where: {
      title: { contains: query, mode: 'insensitive' },
      userId
    },
    select: { title: true },
    take: 3
  });
  suggestions.push(...widgets.map(w => w.title));

  // Page titles
  const matchingPages = staticPages
    .filter(p => p.title.toLowerCase().includes(query))
    .slice(0, 3)
    .map(p => p.title);
  suggestions.push(...matchingPages);

  // Remove duplicates and limit
  const uniqueSuggestions = [...new Set(suggestions)].slice(0, 8);

  return res.json({
    success: true,
    data: uniqueSuggestions,
    message: 'Suggestions retrieved successfully'
  });
}));

/**
 * @route   GET /api/search/recent
 * @desc    Get user's recent searches and activity
 * @access  Private
 */
router.get('/recent', asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  if (!userId) {
    throw new AuthorizationError('User not authenticated');
  }

  // Get recent dashboards the user has accessed
  const recentActivity = await prisma.activityLog.findMany({
    where: {
      userId,
      action: { in: ['DASHBOARD_VIEWED', 'DASHBOARD_UPDATED', 'WIDGET_CREATED'] },
      dashboardId: { not: null }
    },
    select: {
      dashboardId: true,
      action: true,
      createdAt: true,
      dashboard: {
        select: {
          id: true,
          title: true
        }
      }
    },
    orderBy: { createdAt: 'desc' },
    take: 10,
    distinct: ['dashboardId']
  });

  const recentItems = recentActivity
    .filter(a => a.dashboard)
    .map(a => ({
      id: a.dashboardId,
      type: 'dashboard' as const,
      title: a.dashboard!.title,
      url: `/dashboard/${a.dashboardId}`,
      icon: 'LayoutDashboard',
      lastAccessed: a.createdAt
    }));

  res.json({
    success: true,
    data: recentItems,
    message: 'Recent items retrieved successfully'
  });
}));

// Helper to get icon for widget type
function getWidgetIcon(type: string): string {
  const iconMap: Record<string, string> = {
    CHART_LINE: 'LineChart',
    CHART_BAR: 'BarChart',
    CHART_PIE: 'PieChart',
    CHART_AREA: 'AreaChart',
    CHART_SCATTER: 'ScatterChart',
    TABLE: 'Table',
    METRIC: 'Hash',
    TEXT: 'FileText',
    IMAGE: 'Image',
    MAP: 'Map',
    HEATMAP: 'Grid',
    GAUGE: 'Gauge',
    CUSTOM: 'Puzzle'
  };
  return iconMap[type] || 'LayoutGrid';
}

export default router;
