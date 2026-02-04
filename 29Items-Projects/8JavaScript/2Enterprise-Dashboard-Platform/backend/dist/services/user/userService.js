import { Prisma } from '@prisma/client';
import { prisma } from '@/config/database.js';
import { extendedCacheService as cacheService } from '@/services/cache/cacheService.js';
import { authService } from '@/middleware/auth/authMiddleware.js';
import { logger, logBusinessEvent } from '@/utils/logger.js';
import { NotFoundError, ValidationError, AuthorizationError, DatabaseError, ConflictError } from '@/utils/errors.js';
export class UserService {
    async getAllUsers(query, requestingUserId) {
        const { page, limit, search, role, isActive, sortBy, sortOrder } = query;
        const requestingUser = await this.getUserById(requestingUserId);
        if (!requestingUser || !this.isAdmin(requestingUser.role)) {
            throw new AuthorizationError('Admin access required');
        }
        const queryHash = this.createQueryHash(query);
        const cacheKey = `users:all:${queryHash}`;
        try {
            const where = {
                ...(search && {
                    OR: [
                        { email: { contains: search, mode: 'insensitive' } },
                        { firstName: { contains: search, mode: 'insensitive' } },
                        { lastName: { contains: search, mode: 'insensitive' } },
                    ]
                }),
                ...(role && { role }),
                ...(isActive !== undefined && { isActive }),
            };
            const skip = (page - 1) * limit;
            const [users, total] = await Promise.all([
                prisma.user.findMany({
                    where,
                    skip,
                    take: limit,
                    orderBy: {
                        [sortBy]: sortOrder,
                    },
                    include: {
                        profile: true,
                        _count: {
                            select: {
                                dashboards: true,
                                widgets: true,
                            },
                        },
                    },
                }),
                prisma.user.count({ where }),
            ]);
            const sanitizedUsers = users.map(user => ({
                ...user,
                password: undefined,
            }));
            const pages = Math.ceil(total / limit);
            const hasNext = page < pages;
            const hasPrev = page > 1;
            const result = {
                data: sanitizedUsers,
                total,
                page,
                limit,
                pages,
                hasNext,
                hasPrev,
            };
            await cacheService.set(cacheKey, result, 300);
            logBusinessEvent('USERS_FETCHED', {
                requestingUserId,
                count: users.length,
                total,
                page,
                filters: { search, role, isActive },
            });
            return result;
        }
        catch (error) {
            logger.error('Failed to fetch all users', { query, requestingUserId, error });
            throw new DatabaseError('Failed to fetch users', { query });
        }
    }
    async getUserById(userId, includeProfile = true) {
        const cacheKey = `user:${userId}`;
        const cached = await cacheService.getUserById(cacheKey);
        if (cached) {
            logger.debug('User cache hit', { userId });
            return cached;
        }
        try {
            const user = await prisma.user.findUnique({
                where: { id: userId },
                include: {
                    profile: includeProfile,
                    ...(includeProfile && {
                        dashboards: {
                            select: {
                                id: true,
                                title: true,
                                updatedAt: true,
                                isPublic: true,
                            },
                            orderBy: {
                                updatedAt: 'desc',
                            },
                            take: 5,
                        },
                    }),
                },
            });
            if (user) {
                const sanitizedUser = {
                    ...user,
                    password: undefined,
                };
                await cacheService.setUser(userId, sanitizedUser, 300);
                return sanitizedUser;
            }
            return null;
        }
        catch (error) {
            logger.error('Failed to fetch user by ID', { userId, error });
            throw new DatabaseError('Failed to fetch user', { userId });
        }
    }
    async getUserByEmail(email) {
        try {
            const user = await prisma.user.findUnique({
                where: { email: email.toLowerCase() },
                include: {
                    profile: true,
                },
            });
            if (user) {
                const sanitizedUser = {
                    ...user,
                    password: undefined,
                };
                return sanitizedUser;
            }
            return null;
        }
        catch (error) {
            logger.error('Failed to fetch user by email', { email, error });
            throw new DatabaseError('Failed to fetch user', { email });
        }
    }
    async createUser(data) {
        const { email, password, firstName, lastName, role = 'USER', isActive = true } = data;
        try {
            const existingUser = await this.getUserByEmail(email);
            if (existingUser) {
                throw new ConflictError('User with this email already exists');
            }
            const hashedPassword = await authService.hashPassword(password);
            const user = await prisma.user.create({
                data: {
                    email: email.toLowerCase().trim(),
                    password: hashedPassword,
                    firstName: firstName?.trim(),
                    lastName: lastName?.trim(),
                    role,
                    isActive,
                    profile: {
                        create: {
                            theme: 'light',
                            timezone: 'UTC',
                            language: 'en',
                            notifications: JSON.stringify({
                                email: true,
                                push: true,
                                dashboard: true,
                                security: true,
                            }),
                        },
                    },
                },
                include: {
                    profile: true,
                },
            });
            const sanitizedUser = {
                ...user,
                password: undefined,
            };
            await cacheService.setUser(user.id, sanitizedUser);
            logBusinessEvent('USER_CREATED', {
                userId: user.id,
                email: user.email,
                role: user.role,
            });
            return sanitizedUser;
        }
        catch (error) {
            if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === 'P2002') {
                throw new ConflictError('User with this email already exists');
            }
            logger.error('Failed to create user', { data, error });
            throw new DatabaseError('Failed to create user', data);
        }
    }
    async updateUser(userId, data, requestingUserId) {
        const { email, firstName, lastName, role, isActive } = data;
        try {
            const canEdit = await this.canEditUser(userId, requestingUserId);
            if (!canEdit) {
                throw new AuthorizationError('Cannot edit this user');
            }
            if (email) {
                const existingUser = await prisma.user.findFirst({
                    where: {
                        email: email.toLowerCase(),
                        id: { not: userId },
                    },
                });
                if (existingUser) {
                    throw new ConflictError('Email is already in use');
                }
            }
            const updateData = {};
            if (email !== undefined)
                updateData.email = email.toLowerCase().trim();
            if (firstName !== undefined)
                updateData.firstName = firstName?.trim();
            if (lastName !== undefined)
                updateData.lastName = lastName?.trim();
            if (role !== undefined)
                updateData.role = role;
            if (isActive !== undefined)
                updateData.isActive = isActive;
            const user = await prisma.user.update({
                where: { id: userId },
                data: updateData,
                include: {
                    profile: true,
                },
            });
            const sanitizedUser = {
                ...user,
                password: undefined,
            };
            await cacheService.invalidateUser(userId);
            logBusinessEvent('USER_UPDATED', {
                userId,
                requestingUserId,
                changes: Object.keys(data),
            });
            return sanitizedUser;
        }
        catch (error) {
            if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === 'P2002') {
                throw new ConflictError('Email is already in use');
            }
            logger.error('Failed to update user', { userId, data, requestingUserId, error });
            throw new DatabaseError('Failed to update user', { userId, data });
        }
    }
    async updateUserProfile(userId, data, requestingUserId) {
        try {
            const canEdit = userId === requestingUserId || await this.isUserAdmin(requestingUserId);
            if (!canEdit) {
                throw new AuthorizationError('Cannot edit this profile');
            }
            const { theme, timezone, language, notifications, avatar, bio } = data;
            const updateData = {};
            if (theme !== undefined)
                updateData.theme = theme;
            if (timezone !== undefined)
                updateData.timezone = timezone;
            if (language !== undefined)
                updateData.language = language;
            if (notifications !== undefined)
                updateData.notifications = JSON.stringify(notifications);
            if (avatar !== undefined)
                updateData.avatar = avatar;
            if (bio !== undefined)
                updateData.bio = bio;
            const profile = await prisma.userProfile.upsert({
                where: { userId },
                create: {
                    userId,
                    theme: theme || 'light',
                    timezone: timezone || 'UTC',
                    language: language || 'en',
                    notifications: JSON.stringify(notifications || {}),
                    avatar,
                    bio,
                },
                update: updateData,
            });
            await cacheService.invalidateUser(userId);
            logBusinessEvent('USER_PROFILE_UPDATED', {
                userId,
                requestingUserId,
                changes: Object.keys(data),
            });
            return profile;
        }
        catch (error) {
            logger.error('Failed to update user profile', { userId, data, requestingUserId, error });
            throw new DatabaseError('Failed to update user profile', { userId });
        }
    }
    async deleteUser(userId, requestingUserId) {
        try {
            const requestingUser = await this.getUserById(requestingUserId);
            if (!requestingUser || !this.isAdmin(requestingUser.role)) {
                throw new AuthorizationError('Admin access required');
            }
            if (userId === requestingUserId) {
                throw new ValidationError('Cannot delete your own account');
            }
            const user = await prisma.user.update({
                where: { id: userId },
                data: {
                    isActive: false,
                    email: `deleted_${Date.now()}_${user.email}`,
                },
                include: {
                    profile: true,
                },
            });
            await cacheService.invalidateUserData(userId);
            logBusinessEvent('USER_DELETED', {
                userId,
                requestingUserId,
                email: user.email,
            });
            return true;
        }
        catch (error) {
            logger.error('Failed to delete user', { userId, requestingUserId, error });
            throw new DatabaseError('Failed to delete user', { userId });
        }
    }
    async getUserStats(userId, requestingUserId) {
        try {
            const canView = userId === requestingUserId || await this.isUserAdmin(requestingUserId);
            if (!canView) {
                throw new AuthorizationError('Cannot view user statistics');
            }
            const cacheKey = `user_stats:${userId}`;
            const cached = await cacheService.get(cacheKey);
            if (cached) {
                return cached;
            }
            const stats = await prisma.user.findUnique({
                where: { id: userId },
                include: {
                    _count: {
                        select: {
                            dashboards: true,
                            widgets: true,
                            dashboardShares: true,
                        },
                    },
                },
            });
            if (!stats) {
                throw new NotFoundError('User');
            }
            const [recentDashboards, publicDashboards, totalViews,] = await Promise.all([
                prisma.dashboard.findMany({
                    where: { userId },
                    orderBy: { updatedAt: 'desc' },
                    take: 5,
                    select: {
                        id: true,
                        title: true,
                        updatedAt: true,
                        isPublic: true,
                    },
                }),
                prisma.dashboard.count({
                    where: { userId, isPublic: true },
                }),
                prisma.dashboardAnalytics.aggregate({
                    where: {
                        dashboard: { userId },
                    },
                    _sum: {
                        views: true,
                        uniqueViews: true,
                    },
                }),
            ]);
            const result = {
                user: {
                    id: stats.id,
                    email: stats.email,
                    firstName: stats.firstName,
                    lastName: stats.lastName,
                    role: stats.role,
                    isActive: stats.isActive,
                    createdAt: stats.createdAt,
                    lastLogin: stats.lastLogin,
                },
                counts: {
                    totalDashboards: stats._count.dashboards,
                    totalWidgets: stats._count.widgets,
                    sharedDashboards: stats._count.dashboardShares,
                    publicDashboards,
                },
                activity: {
                    totalViews: totalViews._sum.views || 0,
                    totalUniqueViews: totalViews._sum.uniqueViews || 0,
                    recentDashboards,
                },
            };
            await cacheService.set(cacheKey, result, 300);
            return result;
        }
        catch (error) {
            logger.error('Failed to get user stats', { userId, requestingUserId, error });
            throw new DatabaseError('Failed to get user statistics', { userId });
        }
    }
    async searchUsers(query, requestingUserId, limit = 10) {
        try {
            const requestingUser = await this.getUserById(requestingUserId);
            if (!requestingUser) {
                throw new AuthorizationError('User not found');
            }
            const cacheKey = `user_search:${query}:${limit}`;
            const cached = await cacheService.get(cacheKey);
            if (cached) {
                return cached;
            }
            const users = await prisma.user.findMany({
                where: {
                    AND: [
                        { isActive: true },
                        {
                            OR: [
                                { email: { contains: query, mode: 'insensitive' } },
                                { firstName: { contains: query, mode: 'insensitive' } },
                                { lastName: { contains: query, mode: 'insensitive' } },
                            ],
                        },
                    ],
                },
                select: {
                    id: true,
                    email: true,
                    firstName: true,
                    lastName: true,
                    role: true,
                    profile: {
                        select: {
                            avatar: true,
                        },
                    },
                },
                take: limit,
                orderBy: [
                    { firstName: 'asc' },
                    { lastName: 'asc' },
                ],
            });
            const result = users;
            await cacheService.set(cacheKey, result, 600);
            return result;
        }
        catch (error) {
            logger.error('Failed to search users', { query, requestingUserId, error });
            throw new DatabaseError('Failed to search users', { query });
        }
    }
    isAdmin(role) {
        return role === 'SUPER_ADMIN' || role === 'ADMIN';
    }
    async isUserAdmin(userId) {
        try {
            const user = await this.getUserById(userId);
            return user ? this.isAdmin(user.role) : false;
        }
        catch {
            return false;
        }
    }
    async canEditUser(targetUserId, requestingUserId) {
        if (targetUserId === requestingUserId) {
            return true;
        }
        return await this.isUserAdmin(requestingUserId);
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
export const userService = new UserService();
//# sourceMappingURL=userService.js.map