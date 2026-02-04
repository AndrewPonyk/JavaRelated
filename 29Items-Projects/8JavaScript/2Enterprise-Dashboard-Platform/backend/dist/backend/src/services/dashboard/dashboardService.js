import { Prisma } from '@prisma/client';
import { prisma } from '@/config/database.js';
import { extendedCacheService as cacheService } from '@/services/cache/cacheService.js';
import { logger, logBusinessEvent } from '@/utils/logger.js';
import { NotFoundError, ValidationError, AuthorizationError, DatabaseError } from '@/utils/errors.js';
export class DashboardService {
    async getUserDashboards(userId, query) {
        const { page, limit, search, sortBy, sortOrder, isPublic, isTemplate, tags } = query;
        const queryHash = this.createQueryHash(query);
        const cacheKey = `dashboards:${userId}:${queryHash}`;
        const cached = await cacheService.get(cacheKey);
        if (cached) {
            logger.debug('Dashboard list cache hit', { userId, cacheKey });
            return cached;
        }
        try {
            const where = {
                OR: [
                    { userId },
                    {
                        isPublic: true,
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
            };
            const skip = (page - 1) * limit;
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
            const pages = Math.ceil(total / limit);
            const hasNext = page < pages;
            const hasPrev = page > 1;
            const result = {
                data: dashboards,
                total,
                page,
                limit,
                pages,
                hasNext,
                hasPrev,
            };
            await cacheService.set(cacheKey, result, 600);
            logBusinessEvent('DASHBOARDS_FETCHED', {
                userId,
                count: dashboards.length,
                total,
                page,
                filters: { search, isPublic, isTemplate },
            });
            return result;
        }
        catch (error) {
            logger.error('Failed to fetch user dashboards', { userId, query, error });
            throw new DatabaseError('Failed to fetch dashboards', { userId, query });
        }
    }
    async getDashboard(dashboardId, userId) {
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
                        { userId },
                        { isPublic: true },
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
                await cacheService.setDashboard(cacheKey, dashboard, 600);
                await this.recordDashboardView(dashboardId, userId);
                logBusinessEvent('DASHBOARD_VIEWED', {
                    dashboardId,
                    userId,
                    ownerId: dashboard.userId,
                    title: dashboard.title,
                });
            }
            return dashboard;
        }
        catch (error) {
            logger.error('Failed to fetch dashboard', { dashboardId, userId, error });
            throw new DatabaseError('Failed to fetch dashboard', { dashboardId });
        }
    }
    async createDashboard(data) {
        const { title, description, isPublic = false, layout = [], settings = {}, userId } = data;
        try {
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
            await cacheService.invalidateUserDashboards(userId);
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
            return dashboard;
        }
        catch (error) {
            if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === 'P2002') {
                throw new ValidationError('Dashboard title must be unique');
            }
            logger.error('Failed to create dashboard', { data, error });
            throw new DatabaseError('Failed to create dashboard', data);
        }
    }
    async updateDashboard(dashboardId, userId, data) {
        const { title, description, isPublic, layout, settings } = data;
        try {
            const existingDashboard = await this.checkDashboardPermissions(dashboardId, userId, 'WRITE');
            if (!existingDashboard) {
                return null;
            }
            let slug = existingDashboard.slug;
            if (title && title !== existingDashboard.title) {
                slug = this.generateSlug(title);
                const existingSlug = await this.checkSlugExists(slug, dashboardId);
                if (existingSlug) {
                    slug = `${slug}-${Date.now()}`;
                }
            }
            const updateData = {};
            if (title !== undefined) {
                updateData.title = title.trim();
                updateData.slug = slug;
            }
            if (description !== undefined)
                updateData.description = description?.trim();
            if (isPublic !== undefined)
                updateData.isPublic = isPublic;
            if (layout !== undefined)
                updateData.layout = JSON.stringify(layout);
            if (settings !== undefined)
                updateData.settings = JSON.stringify(settings);
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
            await Promise.all([
                cacheService.invalidateDashboard(dashboardId),
                cacheService.invalidateUserDashboards(existingDashboard.userId),
            ]);
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
            return dashboard;
        }
        catch (error) {
            logger.error('Failed to update dashboard', { dashboardId, userId, data, error });
            throw new DatabaseError('Failed to update dashboard', { dashboardId, data });
        }
    }
    async deleteDashboard(dashboardId, userId) {
        try {
            const existingDashboard = await this.checkDashboardPermissions(dashboardId, userId, 'ADMIN');
            if (!existingDashboard) {
                return false;
            }
            await prisma.$transaction(async (tx) => {
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
                await tx.dashboard.delete({
                    where: { id: dashboardId },
                });
            });
            await Promise.all([
                cacheService.invalidateDashboardData(dashboardId),
                cacheService.invalidateUserDashboards(existingDashboard.userId),
            ]);
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
        }
        catch (error) {
            logger.error('Failed to delete dashboard', { dashboardId, userId, error });
            throw new DatabaseError('Failed to delete dashboard', { dashboardId });
        }
    }
    async shareDashboard(dashboardId, userId, emails, permission = 'READ') {
        try {
            const dashboard = await this.checkDashboardPermissions(dashboardId, userId, 'WRITE');
            if (!dashboard) {
                throw new AuthorizationError('Cannot share this dashboard');
            }
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
            const sharePromises = targetUsers.map(async (targetUser) => {
                try {
                    const existingShare = await prisma.dashboardShare.findUnique({
                        where: {
                            dashboardId_userId: {
                                dashboardId,
                                userId: targetUser.id,
                            },
                        },
                    });
                    if (existingShare) {
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
                    }
                    else {
                        return await prisma.dashboardShare.create({
                            data: {
                                dashboardId,
                                userId: targetUser.id,
                                permission,
                                sharedBy: userId,
                            },
                        });
                    }
                }
                catch (error) {
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
            await cacheService.invalidateDashboard(dashboardId);
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
        }
        catch (error) {
            logger.error('Failed to share dashboard', { dashboardId, userId, emails, error });
            throw new DatabaseError('Failed to share dashboard', { dashboardId, emails });
        }
    }
    async getDashboardAnalytics(dashboardId, userId, query) {
        const { startDate, endDate, granularity } = query;
        const dashboard = await this.checkDashboardPermissions(dashboardId, userId, 'READ');
        if (!dashboard) {
            throw new NotFoundError('Dashboard');
        }
        const queryHash = this.createQueryHash(query);
        const cacheKey = `analytics:${dashboardId}:${queryHash}`;
        const cached = await cacheService.getAnalytics(dashboardId, queryHash);
        if (cached) {
            return cached;
        }
        try {
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
            await cacheService.setAnalytics(dashboardId, queryHash, processedData, 900);
            return processedData;
        }
        catch (error) {
            logger.error('Failed to get dashboard analytics', { dashboardId, userId, query, error });
            throw new DatabaseError('Failed to get analytics data', { dashboardId });
        }
    }
    generateSlug(title) {
        return title
            .toLowerCase()
            .trim()
            .replace(/[^\w\s-]/g, '')
            .replace(/[\s_-]+/g, '-')
            .replace(/^-+|-+$/g, '');
    }
    async checkSlugExists(slug, excludeId) {
        const existing = await prisma.dashboard.findFirst({
            where: {
                slug,
                ...(excludeId && { id: { not: excludeId } }),
            },
            select: { id: true },
        });
        return !!existing;
    }
    async checkDashboardPermissions(dashboardId, userId, requiredPermission) {
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
        if (!dashboard)
            return null;
        if (dashboard.userId === userId)
            return dashboard;
        if (dashboard.user.role === 'SUPER_ADMIN' || dashboard.user.role === 'ADMIN') {
            return dashboard;
        }
        if (dashboard.isPublic && requiredPermission === 'READ') {
            return dashboard;
        }
        const share = dashboard.dashboardShares[0];
        if (share) {
            const hasPermission = this.checkPermissionLevel(share.permission, requiredPermission);
            return hasPermission ? dashboard : null;
        }
        return null;
    }
    checkPermissionLevel(userPermission, requiredPermission) {
        const permissionLevels = {
            READ: 1,
            WRITE: 2,
            ADMIN: 3,
        };
        const userLevel = permissionLevels[userPermission] || 0;
        const requiredLevel = permissionLevels[requiredPermission] || 0;
        return userLevel >= requiredLevel;
    }
    async recordDashboardView(dashboardId, userId) {
        try {
            const today = new Date();
            today.setUTCHours(0, 0, 0, 0);
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
                },
            });
        }
        catch (error) {
            logger.warn('Failed to record dashboard view', { dashboardId, userId, error });
        }
    }
    async logActivity(data) {
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
        }
        catch (error) {
            logger.warn('Failed to log activity', { data, error });
        }
    }
    createQueryHash(query) {
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
export const dashboardService = new DashboardService();
//# sourceMappingURL=dashboardService.js.map