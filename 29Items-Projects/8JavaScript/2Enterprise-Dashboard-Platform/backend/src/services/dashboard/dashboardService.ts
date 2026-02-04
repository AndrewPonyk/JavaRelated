import { Prisma } from '@prisma/client';
import { prisma } from '@/config/database.js';
import { extendedCacheService as cacheService } from '@/services/cache/cacheService.js';
import { logger, logBusinessEvent } from '@/utils/logger.js';
import { NotFoundError, ValidationError, AuthorizationError, DatabaseError } from '@/utils/errors.js';
import type {
  Dashboard,
  Widget,
  User,
  DashboardShare,
  DashboardAnalytics,
  ActivityLog,
  SharePermission
} from '@prisma/client';

// Extended types with relations
type DashboardWithRelations = Dashboard & {
  user?: User;
  widgets?: Widget[];
  shares?: DashboardShare[];
  analytics?: DashboardAnalytics[];
};

// Service interfaces
interface DashboardQuery {
  page: number;
  limit: number;
  search?: string;
  sortBy: 'createdAt' | 'updatedAt' | 'title';
  sortOrder: 'asc' | 'desc';
  isPublic?: boolean;
  isTemplate?: boolean;
  tags?: string[];
}

interface PaginatedResult<T> {
  data: T[];
  total: number;
  page: number;
  limit: number;
  pages: number;
  hasNext: boolean;
  hasPrev: boolean;
}

interface CreateDashboardData {
  title: string;
  description?: string;
  isPublic?: boolean;
  layout?: any[];
  settings?: any;
  userId: string;
}

interface UpdateDashboardData {
  title?: string;
  description?: string;
  isPublic?: boolean;
  layout?: any[];
  settings?: any;
}

interface AnalyticsQuery {
  startDate?: string;
  endDate?: string;
  granularity: 'hour' | 'day' | 'week' | 'month';
}

export class DashboardService {
  // Get user's dashboards with pagination and filtering
  async getUserDashboards(
    userId: string,
    query: DashboardQuery
  ): Promise<PaginatedResult<DashboardWithRelations>> {
    const { page, limit, search, sortBy, sortOrder, isPublic, isTemplate, tags } = query;

    // Create cache key for this query
    const queryHash = this.createQueryHash(query);
    const cacheKey = `dashboards:${userId}:${queryHash}`;

    // Try to get from cache first
    const cached = await cacheService.get<PaginatedResult<DashboardWithRelations>>(cacheKey);
    if (cached) {
      logger.debug('Dashboard list cache hit', { userId, cacheKey });
      return cached;
    }

    try {
      // Build where clause
      const where: Prisma.DashboardWhereInput = {
        OR: [
          { userId }, // User's own dashboards
          {
            isPublic: true, // Public dashboards
            isTemplate: false
          },
          {
            dashboardShares: {
              some: {
                userId,
              }
            }
          }
        ],
        ...(search && {
          OR: [
            { title: { contains: search, mode: 'insensitive' } },
            { description: { contains: search, mode: 'insensitive' } },
          ]
        }),
        ...(isPublic !== undefined && { isPublic }),
        ...(isTemplate !== undefined && { isTemplate }),
        // TODO: Add tags filtering when tags are implemented
      };

      // Calculate pagination
      const skip = (page - 1) * limit;

      // Execute queries in parallel
      const [dashboards, total] = await Promise.all([
        prisma.dashboard.findMany({
          where,
          skip,
          take: limit,
          orderBy: {
            [sortBy]: sortOrder,
          },
          include: {
            user: {
              select: {
                id: true,
                email: true,
                firstName: true,
                lastName: true,
              },
            },
            widgets: {
              select: {
                id: true,
                title: true,
                type: true,
                position: true,
              },
              orderBy: {
                createdAt: 'asc',
              },
            },
            dashboardShares: {
              where: {
                userId,
              },
              select: {
                permission: true,
              },
            },
            _count: {
              select: {
                widgets: true,
                dashboardShares: true,
              },
            },
          },
        }),
        prisma.dashboard.count({ where }),
      ]);

      // Calculate pagination info
      const pages = Math.ceil(total / limit);
      const hasNext = page < pages;
      const hasPrev = page > 1;

      const result: PaginatedResult<DashboardWithRelations> = {
        data: dashboards as unknown as DashboardWithRelations[],
        total,
        page,
        limit,
        pages,
        hasNext,
        hasPrev,
      };

      // Cache the result for 10 minutes
      await cacheService.set(cacheKey, result, 600);

      logBusinessEvent('DASHBOARDS_FETCHED', {
        userId,
        count: dashboards.length,
        total,
        page,
        filters: { search, isPublic, isTemplate },
      });

      return result;

    } catch (error) {
      logger.error('Failed to fetch user dashboards', { userId, query, error });
      throw new DatabaseError('Failed to fetch dashboards', { userId, query });
    }
  }

  // Get single dashboard by ID
  async getDashboard(dashboardId: string, userId: string): Promise<DashboardWithRelations | null> {
    // Try cache first
    const cacheKey = `dashboard:${dashboardId}:${userId}`;
    const cached = await cacheService.getDashboard(cacheKey);
    if (cached) {
      logger.debug('Dashboard cache hit', { dashboardId, userId });
      return cached;
    }

    try {
      const dashboard = await prisma.dashboard.findFirst({
        where: {
          id: dashboardId,
          OR: [
            { userId }, // Owner
            { isPublic: true }, // Public dashboard
            {
              dashboardShares: {
                some: { userId }
              }
            }
          ]
        },
        include: {
          user: {
            select: {
              id: true,
              email: true,
              firstName: true,
              lastName: true,
              profile: {
                select: {
                  avatar: true,
                }
              }
            },
          },
          widgets: {
            orderBy: {
              createdAt: 'asc',
            },
            include: {
              user: {
                select: {
                  id: true,
                  firstName: true,
                  lastName: true,
                },
              },
            },
          },
          dashboardShares: {
            include: {
              user: {
                select: {
                  id: true,
                  email: true,
                  firstName: true,
                  lastName: true,
                },
              },
            },
          },
        },
      });

      if (dashboard) {
        // Cache for 10 minutes
        await cacheService.setDashboard(cacheKey, dashboard, 600);

        // Log analytics event
        await this.recordDashboardView(dashboardId, userId);

        logBusinessEvent('DASHBOARD_VIEWED', {
          dashboardId,
          userId,
          ownerId: dashboard.userId,
          title: dashboard.title,
        });
      }

      return dashboard as unknown as DashboardWithRelations;

    } catch (error) {
      logger.error('Failed to fetch dashboard', { dashboardId, userId, error });
      throw new DatabaseError('Failed to fetch dashboard', { dashboardId });
    }
  }

  // Create new dashboard
  async createDashboard(data: CreateDashboardData): Promise<DashboardWithRelations> {
    const { title, description, isPublic = false, layout = [], settings = {}, userId } = data;

    try {
      // Generate unique slug
      let slug = this.generateSlug(title);
      const existingSlug = await this.checkSlugExists(slug);
      if (existingSlug) {
        slug = `${slug}-${Date.now()}`;
      }

      const dashboard = await prisma.dashboard.create({
        data: {
          title: title.trim(),
          description: description?.trim(),
          slug,
          isPublic,
          layout: JSON.stringify(layout),
          settings: JSON.stringify(settings),
          userId,
        },
        include: {
          user: {
            select: {
              id: true,
              email: true,
              firstName: true,
              lastName: true,
            },
          },
          widgets: true,
        },
      });

      // Invalidate user's dashboard cache
      await cacheService.invalidateUserDashboards(userId);

      // Log activity
      await this.logActivity({
        action: 'DASHBOARD_CREATED',
        entity: 'dashboard',
        entityId: dashboard.id,
        userId,
        details: {
          title: dashboard.title,
          isPublic,
        },
      });

      logBusinessEvent('DASHBOARD_CREATED', {
        dashboardId: dashboard.id,
        userId,
        title: dashboard.title,
        isPublic,
      });

      return dashboard as DashboardWithRelations;

    } catch (error) {
      if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === 'P2002') {
        throw new ValidationError('Dashboard title must be unique');
      }
      logger.error('Failed to create dashboard', { data, error });
      throw new DatabaseError('Failed to create dashboard', data);
    }
  }

  // Update existing dashboard
  async updateDashboard(
    dashboardId: string,
    userId: string,
    data: UpdateDashboardData
  ): Promise<DashboardWithRelations | null> {
    const { title, description, isPublic, layout, settings } = data;

    try {
      // Check permissions
      const existingDashboard = await this.checkDashboardPermissions(dashboardId, userId, 'WRITE');
      if (!existingDashboard) {
        return null;
      }

      // Generate new slug if title changed
      let slug = existingDashboard.slug;
      if (title && title !== existingDashboard.title) {
        slug = this.generateSlug(title);
        const existingSlug = await this.checkSlugExists(slug, dashboardId);
        if (existingSlug) {
          slug = `${slug}-${Date.now()}`;
        }
      }

      // Prepare update data
      const updateData: Prisma.DashboardUpdateInput = {};
      if (title !== undefined) {
        updateData.title = title.trim();
        updateData.slug = slug;
      }
      if (description !== undefined) updateData.description = description?.trim();
      if (isPublic !== undefined) updateData.isPublic = isPublic;
      if (layout !== undefined) updateData.layout = JSON.stringify(layout);
      if (settings !== undefined) updateData.settings = JSON.stringify(settings);

      const dashboard = await prisma.dashboard.update({
        where: { id: dashboardId },
        data: updateData,
        include: {
          user: {
            select: {
              id: true,
              email: true,
              firstName: true,
              lastName: true,
            },
          },
          widgets: {
            orderBy: {
              createdAt: 'asc',
            },
          },
        },
      });

      // Invalidate caches
      await Promise.all([
        cacheService.invalidateDashboard(dashboardId),
        cacheService.invalidateUserDashboards(existingDashboard.userId),
      ]);

      // Log activity
      await this.logActivity({
        action: 'DASHBOARD_UPDATED',
        entity: 'dashboard',
        entityId: dashboardId,
        userId,
        details: {
          changes: data,
          title: dashboard.title,
        },
      });

      logBusinessEvent('DASHBOARD_UPDATED', {
        dashboardId,
        userId,
        title: dashboard.title,
        changes: Object.keys(data),
      });

      return dashboard as DashboardWithRelations;

    } catch (error) {
      logger.error('Failed to update dashboard', { dashboardId, userId, data, error });
      throw new DatabaseError('Failed to update dashboard', { dashboardId, data });
    }
  }

  // Delete dashboard
  async deleteDashboard(dashboardId: string, userId: string): Promise<boolean> {
    try {
      // Check permissions (only owner or admin can delete)
      const existingDashboard = await this.checkDashboardPermissions(dashboardId, userId, 'ADMIN');
      if (!existingDashboard) {
        return false;
      }

      // Delete in transaction to ensure consistency
      await prisma.$transaction(async (tx) => {
        // Delete related records first
        await tx.widget.deleteMany({
          where: { dashboardId },
        });

        await tx.dashboardShare.deleteMany({
          where: { dashboardId },
        });

        await tx.dashboardAnalytics.deleteMany({
          where: { dashboardId },
        });

        await tx.activityLog.deleteMany({
          where: {
            entity: 'dashboard',
            entityId: dashboardId,
          },
        });

        // Finally delete the dashboard
        await tx.dashboard.delete({
          where: { id: dashboardId },
        });
      });

      // Invalidate caches
      await Promise.all([
        cacheService.invalidateDashboardData(dashboardId),
        cacheService.invalidateUserDashboards(existingDashboard.userId),
      ]);

      // Log activity
      await this.logActivity({
        action: 'DASHBOARD_DELETED',
        entity: 'dashboard',
        entityId: dashboardId,
        userId,
        details: {
          title: existingDashboard.title,
        },
      });

      logBusinessEvent('DASHBOARD_DELETED', {
        dashboardId,
        userId,
        title: existingDashboard.title,
      });

      return true;

    } catch (error) {
      logger.error('Failed to delete dashboard', { dashboardId, userId, error });
      throw new DatabaseError('Failed to delete dashboard', { dashboardId });
    }
  }

  // Share dashboard with other users
  async shareDashboard(
    dashboardId: string,
    userId: string,
    emails: string[],
    permission: SharePermission = 'READ'
  ): Promise<{ shared: number; errors: string[] }> {
    try {
      // Check if user can share (owner or admin permission)
      const dashboard = await this.checkDashboardPermissions(dashboardId, userId, 'WRITE');
      if (!dashboard) {
        throw new AuthorizationError('Cannot share this dashboard');
      }

      // Find users by emails
      const targetUsers = await prisma.user.findMany({
        where: {
          email: {
            in: emails.map(email => email.toLowerCase()),
          },
          isActive: true,
        },
        select: {
          id: true,
          email: true,
        },
      });

      const foundEmails = new Set(targetUsers.map(user => user.email));
      const errors = emails.filter(email => !foundEmails.has(email.toLowerCase()));

      // Create shares for found users
      const sharePromises = targetUsers.map(async (targetUser) => {
        try {
          // Check if already shared
          const existingShare = await prisma.dashboardShare.findUnique({
            where: {
              dashboardId_userId: {
                dashboardId,
                userId: targetUser.id,
              },
            },
          });

          if (existingShare) {
            // Update existing share
            return await prisma.dashboardShare.update({
              where: {
                id: existingShare.id,
              },
              data: {
                permission,
                sharedBy: userId,
                sharedAt: new Date(),
              },
            });
          } else {
            // Create new share
            return await prisma.dashboardShare.create({
              data: {
                dashboardId,
                userId: targetUser.id,
                permission,
                sharedBy: userId,
              },
            });
          }
        } catch (error) {
          logger.error('Failed to share with user', {
            dashboardId,
            targetUserId: targetUser.id,
            error
          });
          errors.push(targetUser.email);
          return null;
        }
      });

      const shares = await Promise.all(sharePromises);
      const successfulShares = shares.filter(Boolean).length;

      // Invalidate dashboard cache
      await cacheService.invalidateDashboard(dashboardId);

      // Log activity
      await this.logActivity({
        action: 'DASHBOARD_SHARED',
        entity: 'dashboard',
        entityId: dashboardId,
        userId,
        details: {
          title: dashboard.title,
          sharedWith: emails,
          permission,
          successful: successfulShares,
        },
      });

      logBusinessEvent('DASHBOARD_SHARED', {
        dashboardId,
        userId,
        sharedWith: successfulShares,
        permission,
        errors: errors.length,
      });

      return {
        shared: successfulShares,
        errors,
      };

    } catch (error) {
      logger.error('Failed to share dashboard', { dashboardId, userId, emails, error });
      throw new DatabaseError('Failed to share dashboard', { dashboardId, emails });
    }
  }

  // Get dashboard analytics
  async getDashboardAnalytics(
    dashboardId: string,
    userId: string,
    query: AnalyticsQuery
  ): Promise<any> {
    const { startDate, endDate, granularity } = query;

    // Check permissions
    const dashboard = await this.checkDashboardPermissions(dashboardId, userId, 'READ');
    if (!dashboard) {
      throw new NotFoundError('Dashboard');
    }

    // Create cache key
    const queryHash = this.createQueryHash(query);
    const cacheKey = `analytics:${dashboardId}:${queryHash}`;

    // Try cache first
    const cached = await cacheService.getAnalytics(dashboardId, queryHash);
    if (cached) {
      return cached;
    }

    try {
      // Calculate date range
      const end = endDate ? new Date(endDate) : new Date();
      const start = startDate ? new Date(startDate) : new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);

      const analytics = await prisma.dashboardAnalytics.findMany({
        where: {
          dashboardId,
          date: {
            gte: start,
            lte: end,
          },
          granularity,
        },
        orderBy: {
          date: 'asc',
        },
      });

      // Process analytics data
      const processedData = {
        views: analytics.map(a => ({
          date: a.date,
          views: a.views,
          uniqueViews: a.uniqueViews,
        })),
        summary: {
          totalViews: analytics.reduce((sum, a) => sum + a.views, 0),
          totalUniqueViews: analytics.reduce((sum, a) => sum + a.uniqueViews, 0),
          avgLoadTime: analytics.reduce((sum, a) => sum + (a.avgLoadTime || 0), 0) / analytics.length || 0,
          avgBounceRate: analytics.reduce((sum, a) => sum + (a.bounceRate || 0), 0) / analytics.length || 0,
        },
        dateRange: {
          start: start.toISOString(),
          end: end.toISOString(),
          granularity,
        },
      };

      // Cache for 15 minutes
      await cacheService.setAnalytics(dashboardId, queryHash, processedData, 900);

      return processedData;

    } catch (error) {
      logger.error('Failed to get dashboard analytics', { dashboardId, userId, query, error });
      throw new DatabaseError('Failed to get analytics data', { dashboardId });
    }
  }

  // Helper methods
  private generateSlug(title: string): string {
    return title
      .toLowerCase()
      .trim()
      .replace(/[^\w\s-]/g, '')
      .replace(/[\s_-]+/g, '-')
      .replace(/^-+|-+$/g, '');
  }

  private async checkSlugExists(slug: string, excludeId?: string): Promise<boolean> {
    const existing = await prisma.dashboard.findFirst({
      where: {
        slug,
        ...(excludeId && { id: { not: excludeId } }),
      },
      select: { id: true },
    });
    return !!existing;
  }

  private async checkDashboardPermissions(
    dashboardId: string,
    userId: string,
    requiredPermission: 'READ' | 'WRITE' | 'ADMIN'
  ): Promise<Dashboard | null> {
    const dashboard = await prisma.dashboard.findUnique({
      where: { id: dashboardId },
      include: {
        dashboardShares: {
          where: { userId },
          select: { permission: true },
        },
        user: {
          select: { id: true, role: true },
        },
      },
    });

    if (!dashboard) return null;

    // Owner has all permissions
    if (dashboard.userId === userId) return dashboard;

    // Admin users have all permissions
    if (dashboard.user.role === 'SUPER_ADMIN' || dashboard.user.role === 'ADMIN') {
      return dashboard;
    }

    // Public dashboards allow read access
    if (dashboard.isPublic && requiredPermission === 'READ') {
      return dashboard;
    }

    // Check share permissions
    const share = dashboard.dashboardShares[0];
    if (share) {
      const hasPermission = this.checkPermissionLevel(share.permission, requiredPermission);
      return hasPermission ? dashboard : null;
    }

    return null;
  }

  private checkPermissionLevel(userPermission: SharePermission, requiredPermission: string): boolean {
    const permissionLevels = {
      READ: 1,
      WRITE: 2,
      ADMIN: 3,
    };

    const userLevel = permissionLevels[userPermission] || 0;
    const requiredLevel = permissionLevels[requiredPermission as keyof typeof permissionLevels] || 0;

    return userLevel >= requiredLevel;
  }

  private async recordDashboardView(dashboardId: string, userId: string): Promise<void> {
    try {
      const today = new Date();
      today.setUTCHours(0, 0, 0, 0);

      // Record daily analytics
      await prisma.dashboardAnalytics.upsert({
        where: {
          dashboardId_date_granularity: {
            dashboardId,
            date: today,
            granularity: 'day',
          },
        },
        create: {
          dashboardId,
          date: today,
          granularity: 'day',
          views: 1,
          uniqueViews: 1,
        },
        update: {
          views: {
            increment: 1,
          },
          // For unique views, we'd need additional tracking
        },
      });
    } catch (error) {
      logger.warn('Failed to record dashboard view', { dashboardId, userId, error });
    }
  }

  private async logActivity(data: {
    action: string;
    entity: string;
    entityId: string;
    userId: string;
    details?: any;
    ipAddress?: string;
    userAgent?: string;
  }): Promise<void> {
    try {
      await prisma.activityLog.create({
        data: {
          action: data.action,
          entity: data.entity,
          entityId: data.entityId,
          userId: data.userId,
          details: JSON.stringify(data.details || {}),
          ipAddress: data.ipAddress,
          userAgent: data.userAgent,
        },
      });
    } catch (error) {
      logger.warn('Failed to log activity', { data, error });
    }
  }

  protected createQueryHash(query: any): string {
    const str = JSON.stringify(query, Object.keys(query).sort());
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash;
    }
    return Math.abs(hash).toString(16);
  }
}

// Create and export service instance
export const dashboardService = new DashboardService();