import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { createDashboardController } from '@/controllers/dashboard/dashboardController.js';
import { dashboardFeaturesController } from '@/controllers/dashboard/dashboardFeaturesController.js';
import { dashboardService } from '@/services/dashboard/dashboardService.js';
import { extendedCacheService } from '@/services/cache/cacheService.js';
import { authenticate, authorize, optionalAuthenticate } from '@/middleware/auth/authMiddleware.js';
import { csrfProtection } from '@/middleware/security/csrfMiddleware.js';

const router = Router();

// Create dashboard controller instance
const dashboardController = createDashboardController(dashboardService, extendedCacheService);

// Rate limiting for dashboard operations
const dashboardRateLimit = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 200, // Higher limit for dashboard operations
  message: {
    success: false,
    error: {
      code: 'RATE_LIMIT_EXCEEDED',
      message: 'Too many dashboard requests, please try again later',
      statusCode: 429,
    },
  },
  standardHeaders: true,
  legacyHeaders: false,
});

// Apply rate limiting to all routes
router.use(dashboardRateLimit);

// Public routes (optional authentication for public dashboards)
/**
 * @route   GET /api/dashboards/public
 * @desc    Get public dashboards
 * @access  Public (optional auth for personalization)
 * @params  ?page=1&limit=10&search=query&sortBy=updatedAt&sortOrder=desc
 */
router.get('/public', optionalAuthenticate, async (req, res, next) => {
  // Add isPublic=true to query
  req.query.isPublic = 'true';
  dashboardController.getDashboards(req, res, next);
});

/**
 * @route   GET /api/dashboards/templates
 * @desc    Get dashboard templates
 * @access  Public (optional auth)
 * @params  ?page=1&limit=10&search=query
 */
router.get('/templates', optionalAuthenticate, async (req, res, next) => {
  // Add isTemplate=true to query
  req.query.isTemplate = 'true';
  dashboardController.getDashboards(req, res, next);
});

// Apply authentication to all other routes
router.use(authenticate);

// Dashboard CRUD routes
/**
 * @route   GET /api/dashboards
 * @desc    Get user's dashboards with pagination and filtering
 * @access  Private
 * @params  ?page=1&limit=10&search=query&isPublic=false&isTemplate=false&sortBy=updatedAt&sortOrder=desc
 */
router.get('/', dashboardController.getDashboards);

/**
 * @route   POST /api/dashboards
 * @desc    Create a new dashboard
 * @access  Private
 */
router.post('/', csrfProtection, dashboardController.createDashboard);

/**
 * @route   GET /api/dashboards/:id
 * @desc    Get dashboard by ID
 * @access  Private (owner, shared users, or public)
 */
router.get('/:id', dashboardController.getDashboard);

/**
 * @route   PUT /api/dashboards/:id
 * @desc    Update dashboard
 * @access  Private (owner or write permission)
 */
router.put('/:id', csrfProtection, dashboardController.updateDashboard);

/**
 * @route   DELETE /api/dashboards/:id
 * @desc    Delete dashboard
 * @access  Private (owner or admin permission)
 */
router.delete('/:id', csrfProtection, dashboardController.deleteDashboard);

// Dashboard sharing routes
/**
 * @route   POST /api/dashboards/:id/share
 * @desc    Share dashboard with other users
 * @access  Private (owner or write permission)
 */
router.post('/:id/share', csrfProtection, dashboardController.shareDashboard);

/**
 * @route   GET /api/dashboards/:id/shares
 * @desc    Get dashboard sharing information
 * @access  Private (owner or admin permission)
 */
router.get('/:id/shares', dashboardFeaturesController.getShares);

/**
 * @route   DELETE /api/dashboards/:id/shares/:userId
 * @desc    Remove user access from dashboard
 * @access  Private (owner or admin permission)
 */
router.delete('/:id/shares/:userId', csrfProtection, dashboardFeaturesController.removeShare);

// Dashboard analytics routes
/**
 * @route   GET /api/dashboards/:id/analytics
 * @desc    Get dashboard analytics
 * @access  Private (owner, shared users with read permission, or public)
 * @params  ?startDate=2023-01-01&endDate=2023-12-31&granularity=day
 */
router.get('/:id/analytics', dashboardController.getDashboardAnalytics);

/**
 * @route   POST /api/dashboards/:id/analytics/track
 * @desc    Track dashboard view/interaction
 * @access  Private
 */
router.post('/:id/analytics/track', csrfProtection, dashboardFeaturesController.trackAnalytics);

// Dashboard export routes
/**
 * @route   POST /api/dashboards/:id/export
 * @desc    Export dashboard in various formats
 * @access  Private (owner or read permission)
 * @params  format=pdf|png|csv|json
 */
router.post('/:id/export', csrfProtection, dashboardFeaturesController.exportDashboard);

/**
 * @route   GET /api/dashboards/:id/export/:exportId
 * @desc    Get export status or download file
 * @access  Private
 */
router.get('/:id/export/:exportId', dashboardFeaturesController.getExportStatus);

// Dashboard duplication/cloning
/**
 * @route   POST /api/dashboards/:id/duplicate
 * @desc    Duplicate/clone a dashboard
 * @access  Private
 */
router.post('/:id/duplicate', csrfProtection, dashboardFeaturesController.duplicateDashboard);

// Dashboard versioning
/**
 * @route   GET /api/dashboards/:id/versions
 * @desc    Get dashboard version history
 * @access  Private (owner or admin permission)
 */
router.get('/:id/versions', dashboardFeaturesController.getVersions);

/**
 * @route   POST /api/dashboards/:id/versions
 * @desc    Create a new version snapshot
 * @access  Private (owner or write permission)
 */
router.post('/:id/versions', csrfProtection, dashboardFeaturesController.createVersion);

/**
 * @route   POST /api/dashboards/:id/versions/:versionId/restore
 * @desc    Restore dashboard to a specific version
 * @access  Private (owner or write permission)
 */
router.post('/:id/versions/:versionId/restore', csrfProtection, dashboardFeaturesController.restoreVersion);

// Dashboard embedding
/**
 * @route   POST /api/dashboards/:id/embed
 * @desc    Generate embed token for dashboard
 * @access  Private (owner or read permission)
 */
router.post('/:id/embed', csrfProtection, dashboardFeaturesController.generateEmbedToken);

// Dashboard comments/collaboration
/**
 * @route   GET /api/dashboards/:id/comments
 * @desc    Get dashboard comments
 * @access  Private (shared users with read permission)
 */
router.get('/:id/comments', dashboardFeaturesController.getComments);

/**
 * @route   POST /api/dashboards/:id/comments
 * @desc    Add comment to dashboard
 * @access  Private (shared users with read permission)
 */
router.post('/:id/comments', csrfProtection, dashboardFeaturesController.addComment);

/**
 * @route   PUT /api/dashboards/:id/comments/:commentId
 * @desc    Update a comment
 * @access  Private (comment author or admin)
 */
router.put('/:id/comments/:commentId', csrfProtection, dashboardFeaturesController.updateComment);

/**
 * @route   DELETE /api/dashboards/:id/comments/:commentId
 * @desc    Delete a comment
 * @access  Private (comment author or admin)
 */
router.delete('/:id/comments/:commentId', csrfProtection, dashboardFeaturesController.deleteComment);

export default router;