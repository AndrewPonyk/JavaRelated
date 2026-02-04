import { Request, Response, NextFunction } from 'express';
import { z } from 'zod';
import { prisma } from '@/config/database.js';
import { extendedCacheService as cacheService } from '@/services/cache/cacheService.js';
import { exportService, ExportFormat } from '@/services/dashboard/exportService.js';
import { versioningService } from '@/services/dashboard/versioningService.js';
import { commentService } from '@/services/dashboard/commentService.js';
import { logger, logBusinessEvent } from '@/utils/logger.js';
import { AppError, NotFoundError, ValidationError, AuthorizationError } from '@/utils/errors.js';
import { asyncHandler } from '@/utils/asyncHandler.js';
import jwt from 'jsonwebtoken';
import crypto from 'crypto';

// Request type with authenticated user
interface AuthenticatedRequest extends Request {
  user?: {
    id: string;
    email: string;
    role: string;
  };
}

// Validation schemas
const exportSchema = z.object({
  format: z.enum(['pdf', 'png', 'csv', 'json']).default('pdf'),
  options: z.object({
    quality: z.enum(['low', 'medium', 'high']).optional(),
    includeWidgets: z.boolean().optional(),
    dateRange: z.object({
      start: z.string(),
      end: z.string()
    }).optional(),
    paperSize: z.enum(['a4', 'letter', 'legal']).optional(),
    orientation: z.enum(['portrait', 'landscape']).optional()
  }).optional()
});

const commentSchema = z.object({
  content: z.string().min(1).max(2000),
  parentId: z.string().optional()
});

const trackAnalyticsSchema = z.object({
  event: z.enum(['view', 'interact', 'export', 'share']),
  widgetId: z.string().optional(),
  details: z.record(z.any()).optional()
});

const embedSchema = z.object({
  expiresIn: z.number().min(60).max(86400 * 30).default(86400), // 1 min to 30 days, default 24h
  allowedDomains: z.array(z.string()).optional(),
  permissions: z.object({
    interact: z.boolean().default(true),
    export: z.boolean().default(false)
  }).optional()
});

const paginationSchema = z.object({
  page: z.string().optional().transform(val => val ? parseInt(val) : 1),
  limit: z.string().optional().transform(val => val ? parseInt(val) : 20)
});

export class DashboardFeaturesController {
  /**
   * GET /api/dashboards/:id/shares
   * Get dashboard sharing information
   */
  getShares = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Verify user has access (owner or admin permission)
    const dashboard = await this.verifyDashboardAccess(dashboardId, userId, 'ADMIN');

    // Get all shares for this dashboard
    const shares = await prisma.dashboardShare.findMany({
      where: { dashboardId },
      include: {
        user: {
          select: {
            id: true,
            email: true,
            firstName: true,
            lastName: true,
            profile: {
              select: { avatar: true }
            }
          }
        }
      },
      orderBy: { sharedAt: 'desc' }
    });

    // Get sharedBy user info
    const sharedByIds = [...new Set(shares.map(s => s.sharedBy))];
    const sharedByUsers = await prisma.user.findMany({
      where: { id: { in: sharedByIds } },
      select: { id: true, email: true, firstName: true, lastName: true }
    });
    const sharedByMap = new Map(sharedByUsers.map(u => [u.id, u]));

    const formattedShares = shares.map(share => ({
      id: share.id,
      user: {
        id: share.user.id,
        email: share.user.email,
        firstName: share.user.firstName,
        lastName: share.user.lastName,
        avatar: share.user.profile?.avatar
      },
      permission: share.permission,
      sharedAt: share.sharedAt,
      sharedBy: sharedByMap.get(share.sharedBy) || { id: share.sharedBy },
      expiresAt: share.expiresAt
    }));

    logger.info('Retrieved dashboard shares', { dashboardId, userId, count: shares.length });

    res.json({
      success: true,
      data: formattedShares,
      message: 'Dashboard shares retrieved successfully'
    });
  });

  /**
   * DELETE /api/dashboards/:id/shares/:userId
   * Remove user access from dashboard
   */
  removeShare = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;
    const targetUserId = req.params.userId;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Verify user has access (owner or admin permission)
    await this.verifyDashboardAccess(dashboardId, userId, 'ADMIN');

    // Delete the share
    const deleted = await prisma.dashboardShare.deleteMany({
      where: {
        dashboardId,
        userId: targetUserId
      }
    });

    if (deleted.count === 0) {
      throw new NotFoundError('Share');
    }

    // Invalidate caches
    await cacheService.invalidateDashboard(dashboardId);

    // Log activity
    await prisma.activityLog.create({
      data: {
        action: 'DASHBOARD_SHARE_REMOVED',
        entity: 'dashboard',
        entityId: dashboardId,
        userId,
        details: JSON.stringify({ removedUserId: targetUserId })
      }
    });

    logBusinessEvent('DASHBOARD_SHARE_REMOVED', {
      dashboardId,
      userId,
      removedUserId: targetUserId
    });

    logger.info('Removed dashboard share', { dashboardId, userId, targetUserId });

    res.json({
      success: true,
      message: 'Dashboard share removed successfully'
    });
  });

  /**
   * POST /api/dashboards/:id/analytics/track
   * Track dashboard view/interaction
   */
  trackAnalytics = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const { event, widgetId, details } = trackAnalyticsSchema.parse(req.body);

    // Verify dashboard exists
    const dashboard = await prisma.dashboard.findUnique({
      where: { id: dashboardId }
    });

    if (!dashboard) {
      throw new NotFoundError('Dashboard');
    }

    // Log activity
    await prisma.activityLog.create({
      data: {
        action: `DASHBOARD_${event.toUpperCase()}`,
        entity: 'dashboard',
        entityId: dashboardId,
        dashboardId,
        userId,
        details: JSON.stringify({ event, widgetId, ...details }),
        ipAddress: req.ip,
        userAgent: req.get('User-Agent')
      }
    });

    // Update analytics counters
    if (event === 'view') {
      const today = new Date();
      today.setUTCHours(0, 0, 0, 0);

      await prisma.dashboardAnalytics.upsert({
        where: {
          dashboardId_date_granularity: {
            dashboardId,
            date: today,
            granularity: 'day'
          }
        },
        create: {
          dashboardId,
          date: today,
          granularity: 'day',
          views: 1,
          uniqueViews: 1
        },
        update: {
          views: { increment: 1 }
        }
      });
    }

    logger.debug('Tracked dashboard analytics', { dashboardId, userId, event });

    res.json({
      success: true,
      message: 'Analytics tracked successfully'
    });
  });

  /**
   * POST /api/dashboards/:id/export
   * Export dashboard in various formats
   */
  exportDashboard = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const { format, options } = exportSchema.parse(req.body);

    // Create export job
    const job = await exportService.createExportJob(
      dashboardId,
      userId,
      format as ExportFormat,
      options
    );

    logger.info('Export job created', { dashboardId, userId, exportId: job.id, format });

    res.json({
      success: true,
      data: {
        exportId: job.id,
        format: job.format,
        status: job.status,
        progress: job.progress,
        createdAt: job.createdAt,
        expiresAt: job.expiresAt
      },
      message: 'Export initiated successfully'
    });
  });

  /**
   * GET /api/dashboards/:id/export/:exportId
   * Get export status or download file
   */
  getExportStatus = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;
    const exportId = req.params.exportId!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const job = await exportService.getExportStatus(exportId, userId);

    if (!job) {
      throw new NotFoundError('Export');
    }

    // Verify export belongs to this dashboard
    if (job.dashboardId !== dashboardId) {
      throw new NotFoundError('Export');
    }

    // If download query param is present and export is complete, serve file
    if (req.query.download === 'true' && job.status === 'completed') {
      const file = await exportService.getExportFile(exportId!, userId);
      if (file) {
        res.setHeader('Content-Type', file.mimetype);
        res.setHeader('Content-Disposition', `attachment; filename="${file.filename}"`);
        res.sendFile(file.filepath);
        return;
      }
    }

    res.json({
      success: true,
      data: {
        exportId: job.id,
        format: job.format,
        status: job.status,
        progress: job.progress,
        downloadUrl: job.downloadUrl,
        errorMessage: job.errorMessage,
        createdAt: job.createdAt,
        completedAt: job.completedAt,
        expiresAt: job.expiresAt
      },
      message: 'Export status retrieved successfully'
    });
  });

  /**
   * POST /api/dashboards/:id/duplicate
   * Duplicate/clone a dashboard
   */
  duplicateDashboard = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const { title } = req.body;

    // Get original dashboard
    const original = await prisma.dashboard.findFirst({
      where: {
        id: dashboardId,
        OR: [
          { userId },
          { isPublic: true },
          { dashboardShares: { some: { userId } } }
        ]
      },
      include: {
        widgets: true
      }
    });

    if (!original) {
      throw new NotFoundError('Dashboard');
    }

    // Create duplicate in transaction
    const duplicate = await prisma.$transaction(async (tx) => {
      // Generate unique slug
      const baseSlug = original.slug ? `${original.slug}-copy` : `dashboard-copy-${Date.now()}`;
      let slug = baseSlug;
      let counter = 1;

      while (await tx.dashboard.findUnique({ where: { slug } })) {
        slug = `${baseSlug}-${counter}`;
        counter++;
      }

      // Create new dashboard
      const newDashboard = await tx.dashboard.create({
        data: {
          title: title || `Copy of ${original.title}`,
          description: original.description,
          slug,
          isPublic: false, // Copies are private by default
          isTemplate: false,
          layout: original.layout || [],
          settings: original.settings || {},
          userId
        }
      });

      // Clone widgets
      if (original.widgets.length > 0) {
        const widgetData = original.widgets.map(widget => ({
          id: crypto.randomUUID(),
          title: widget.title,
          description: widget.description,
          type: widget.type,
          config: widget.config || {},
          position: widget.position || {},
          dataSource: widget.dataSource,
          query: widget.query,
          refreshRate: widget.refreshRate,
          dashboardId: newDashboard.id,
          userId
        }));

        await tx.widget.createMany({ data: widgetData });
      }

      return newDashboard;
    });

    // Fetch the complete duplicate with widgets
    const fullDuplicate = await prisma.dashboard.findUnique({
      where: { id: duplicate.id },
      include: {
        widgets: true,
        user: {
          select: { id: true, email: true, firstName: true, lastName: true }
        }
      }
    });

    // Log activity
    await prisma.activityLog.create({
      data: {
        action: 'DASHBOARD_DUPLICATED',
        entity: 'dashboard',
        entityId: duplicate.id,
        userId,
        details: JSON.stringify({
          originalId: dashboardId,
          newId: duplicate.id,
          widgetsCopied: original.widgets.length
        })
      }
    });

    logBusinessEvent('DASHBOARD_DUPLICATED', {
      originalId: dashboardId,
      newId: duplicate.id,
      userId
    });

    logger.info('Dashboard duplicated', {
      originalId: dashboardId,
      newId: duplicate.id,
      userId
    });

    res.status(201).json({
      success: true,
      data: fullDuplicate,
      message: 'Dashboard duplicated successfully'
    });
  });

  /**
   * GET /api/dashboards/:id/versions
   * Get dashboard version history
   */
  getVersions = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const { page, limit } = paginationSchema.parse(req.query);

    const result = await versioningService.getVersions(dashboardId, userId, { page, limit });

    logger.info('Retrieved dashboard versions', {
      dashboardId,
      userId,
      count: result.versions.length,
      total: result.total
    });

    res.json({
      success: true,
      data: result.versions,
      meta: {
        total: result.total,
        page,
        limit,
        pages: Math.ceil(result.total / limit)
      },
      message: 'Dashboard versions retrieved successfully'
    });
  });

  /**
   * POST /api/dashboards/:id/versions
   * Create a new version snapshot
   */
  createVersion = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const { changeDescription } = req.body;

    const version = await versioningService.createVersion(
      dashboardId,
      userId,
      changeDescription
    );

    logger.info('Dashboard version created', {
      dashboardId,
      userId,
      versionId: version.id,
      versionNumber: version.versionNumber
    });

    res.status(201).json({
      success: true,
      data: version,
      message: 'Version created successfully'
    });
  });

  /**
   * POST /api/dashboards/:id/versions/:versionId/restore
   * Restore dashboard to a specific version
   */
  restoreVersion = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;
    const versionId = req.params.versionId!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const restoredDashboard = await versioningService.restoreVersion(
      dashboardId,
      versionId,
      userId
    );

    logger.info('Dashboard restored to version', {
      dashboardId,
      userId,
      versionId
    });

    res.json({
      success: true,
      data: restoredDashboard,
      message: 'Dashboard restored to previous version successfully'
    });
  });

  /**
   * POST /api/dashboards/:id/embed
   * Generate embed token for dashboard
   */
  generateEmbedToken = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const { expiresIn, allowedDomains, permissions } = embedSchema.parse(req.body);

    // Verify user has access
    await this.verifyDashboardAccess(dashboardId, userId, 'READ');

    // Generate embed token
    const embedSecret = process.env.EMBED_JWT_SECRET || process.env.JWT_SECRET || 'embed-secret';
    const embedId = crypto.randomBytes(8).toString('hex');

    const payload = {
      type: 'embed',
      embedId,
      dashboardId,
      userId,
      permissions: permissions || { interact: true, export: false },
      allowedDomains: allowedDomains || ['*']
    };

    const token = jwt.sign(payload, embedSecret, {
      expiresIn,
      issuer: 'enterprise-dashboard'
    });

    const expiresAt = new Date(Date.now() + expiresIn * 1000);

    // Log activity
    await prisma.activityLog.create({
      data: {
        action: 'EMBED_TOKEN_GENERATED',
        entity: 'dashboard',
        entityId: dashboardId,
        userId,
        details: JSON.stringify({
          embedId,
          expiresAt,
          allowedDomains
        })
      }
    });

    logBusinessEvent('DASHBOARD_EMBED_TOKEN_CREATED', {
      dashboardId,
      userId,
      embedId,
      expiresIn
    });

    logger.info('Generated embed token', { dashboardId, userId, embedId });

    res.json({
      success: true,
      data: {
        embedId,
        embedToken: token,
        embedUrl: `/embed/dashboard/${dashboardId}?token=${token}`,
        expiresAt: expiresAt.toISOString(),
        allowedDomains,
        permissions
      },
      message: 'Embed token generated successfully'
    });
  });

  /**
   * GET /api/dashboards/:id/comments
   * Get dashboard comments
   */
  getComments = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const { page, limit } = paginationSchema.parse(req.query);
    const parentId = req.query.parentId as string | undefined;

    const result = await commentService.getComments(dashboardId, userId, {
      page,
      limit,
      parentId
    });

    logger.info('Retrieved dashboard comments', {
      dashboardId,
      userId,
      count: result.comments.length,
      total: result.total
    });

    res.json({
      success: true,
      data: result.comments,
      meta: {
        total: result.total,
        page,
        limit,
        pages: Math.ceil(result.total / limit)
      },
      message: 'Dashboard comments retrieved successfully'
    });
  });

  /**
   * POST /api/dashboards/:id/comments
   * Add comment to dashboard
   */
  addComment = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const { content, parentId } = commentSchema.parse(req.body);

    const comment = await commentService.addComment(dashboardId, userId, {
      content,
      parentId
    });

    logger.info('Added dashboard comment', {
      dashboardId,
      userId,
      commentId: comment.id
    });

    res.status(201).json({
      success: true,
      data: comment,
      message: 'Comment added successfully'
    });
  });

  /**
   * PUT /api/dashboards/:id/comments/:commentId
   * Update a comment
   */
  updateComment = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;
    const commentId = req.params.commentId!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const { content } = commentSchema.parse(req.body);

    const comment = await commentService.updateComment(dashboardId, commentId, userId, {
      content
    });

    logger.info('Updated dashboard comment', {
      dashboardId,
      userId,
      commentId
    });

    res.json({
      success: true,
      data: comment,
      message: 'Comment updated successfully'
    });
  });

  /**
   * DELETE /api/dashboards/:id/comments/:commentId
   * Delete a comment
   */
  deleteComment = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id!;
    const dashboardId = req.params.id!;
    const commentId = req.params.commentId!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    const deleted = await commentService.deleteComment(dashboardId, commentId, userId);

    if (!deleted) {
      throw new NotFoundError('Comment');
    }

    logger.info('Deleted dashboard comment', {
      dashboardId,
      userId,
      commentId
    });

    res.json({
      success: true,
      message: 'Comment deleted successfully'
    });
  });

  // Helper method to verify dashboard access
  private async verifyDashboardAccess(
    dashboardId: string,
    userId: string,
    requiredPermission: 'READ' | 'WRITE' | 'ADMIN'
  ): Promise<any> {
    const dashboard = await prisma.dashboard.findFirst({
      where: {
        id: dashboardId,
        OR: [
          { userId },
          { isPublic: requiredPermission === 'READ' ? true : undefined },
          {
            dashboardShares: {
              some: {
                userId,
                permission: this.getPermissionFilter(requiredPermission)
              }
            }
          }
        ]
      },
      include: {
        user: {
          select: { id: true, role: true }
        }
      }
    });

    if (!dashboard) {
      throw new NotFoundError('Dashboard');
    }

    // Owner has all permissions
    if (dashboard.userId === userId) {
      return dashboard;
    }

    // Check if user is admin
    const user = await prisma.user.findUnique({
      where: { id: userId },
      select: { role: true }
    });

    if (user && ['SUPER_ADMIN', 'ADMIN'].includes(user.role)) {
      return dashboard;
    }

    // For non-owners, verify share permissions
    if (requiredPermission !== 'READ' || !dashboard.isPublic) {
      const share = await prisma.dashboardShare.findUnique({
        where: {
          dashboardId_userId: {
            dashboardId,
            userId
          }
        }
      });

      if (!share || !this.hasPermission(share.permission, requiredPermission)) {
        throw new AuthorizationError('Insufficient permissions');
      }
    }

    return dashboard;
  }

  private getPermissionFilter(required: 'READ' | 'WRITE' | 'ADMIN'): any {
    switch (required) {
      case 'READ':
        return { in: ['READ', 'WRITE', 'ADMIN'] };
      case 'WRITE':
        return { in: ['WRITE', 'ADMIN'] };
      case 'ADMIN':
        return 'ADMIN';
      default:
        return { in: ['READ', 'WRITE', 'ADMIN'] };
    }
  }

  private hasPermission(userPermission: string, requiredPermission: string): boolean {
    const levels: Record<string, number> = {
      READ: 1,
      WRITE: 2,
      ADMIN: 3
    };
    return (levels[userPermission] || 0) >= (levels[requiredPermission] || 0);
  }
}

// Create and export controller instance
export const dashboardFeaturesController = new DashboardFeaturesController();
