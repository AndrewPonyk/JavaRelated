import { Request, Response, NextFunction } from 'express';
import { z } from 'zod';
import { userService } from '@/services/user/userService.js';
import { authService, AuthenticatedRequest } from '@/middleware/auth/authMiddleware.js';
import { prisma } from '@/config/database.js';
import { deleteOldAvatar, getAvatarUrl } from '@/config/upload.js';
import { logger } from '@/utils/logger.js';
import { AppError, ValidationError, NotFoundError } from '@/utils/errors.js';
import { asyncHandler } from '@/utils/asyncHandler.js';
import type { UserRole } from '@prisma/client';

// Validation schemas
const getUsersQuerySchema = z.object({
  page: z.string().optional().transform(val => val ? parseInt(val) : 1),
  limit: z.string().optional().transform(val => val ? parseInt(val) : 10),
  search: z.string().optional(),
  role: z.enum(['SUPER_ADMIN', 'ADMIN', 'MANAGER', 'USER', 'VIEWER']).optional(),
  isActive: z.string().optional().transform(val => val === 'true' ? true : val === 'false' ? false : undefined),
  sortBy: z.enum(['createdAt', 'updatedAt', 'email', 'lastName']).optional().default('updatedAt'),
  sortOrder: z.enum(['asc', 'desc']).optional().default('desc'),
});

const createUserSchema = z.object({
  email: z.string().email('Invalid email format'),
  password: z.string()
    .min(8, 'Password must be at least 8 characters long')
    .regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/, 'Password must contain at least one lowercase letter, one uppercase letter, and one number'),
  firstName: z.string().min(1, 'First name is required').optional(),
  lastName: z.string().min(1, 'Last name is required').optional(),
  role: z.enum(['SUPER_ADMIN', 'ADMIN', 'MANAGER', 'USER', 'VIEWER']).optional().default('USER'),
  isActive: z.boolean().optional().default(true),
});

const updateUserSchema = z.object({
  email: z.string().email('Invalid email format').optional(),
  firstName: z.string().min(1, 'First name cannot be empty').optional(),
  lastName: z.string().min(1, 'Last name cannot be empty').optional(),
  role: z.enum(['SUPER_ADMIN', 'ADMIN', 'MANAGER', 'USER', 'VIEWER']).optional(),
  isActive: z.boolean().optional(),
});

const updateProfileSchema = z.object({
  theme: z.enum(['light', 'dark', 'auto']).optional(),
  timezone: z.string().optional(),
  language: z.string().min(2).max(5).optional(),
  notifications: z.object({
    email: z.boolean().optional(),
    push: z.boolean().optional(),
    dashboard: z.boolean().optional(),
    security: z.boolean().optional(),
    updates: z.boolean().optional(),
  }).optional(),
  avatar: z.string().url('Invalid avatar URL').optional(),
  bio: z.string().max(500, 'Bio cannot exceed 500 characters').optional(),
});

const searchUsersSchema = z.object({
  q: z.string().min(1, 'Search query is required'),
  limit: z.string().optional().transform(val => val ? parseInt(val) : 10),
});

export class UserController {
  /**
   * Get all users (admin only)
   * GET /api/users
   */
  getAllUsers = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id;
    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Validate query parameters
    const queryParams = getUsersQuerySchema.parse(req.query);

    try {
      const users = await userService.getAllUsers(queryParams, userId);

      logger.info('Retrieved all users', {
        requestingUserId: userId,
        count: users.data.length,
        total: users.total,
        page: users.page,
      });

      res.json({
        success: true,
        data: users,
        message: 'Users retrieved successfully',
      });
    } catch (error) {
      logger.error('Failed to get all users', { userId, queryParams, error });
      throw error;
    }
  });

  /**
   * Get current user profile
   * GET /api/users/me
   */
  getCurrentUser = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id;
    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    try {
      const user = await userService.getUserById(userId, true);
      if (!user) {
        throw new NotFoundError('User');
      }

      logger.info('Retrieved current user profile', { userId });

      res.json({
        success: true,
        data: user,
        message: 'User profile retrieved successfully',
      });
    } catch (error) {
      logger.error('Failed to get current user', { userId, error });
      throw error;
    }
  });

  /**
   * Get user by ID
   * GET /api/users/:id
   */
  getUserById = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id;
    const targetUserId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    try {
      const user = await userService.getUserById(targetUserId, true);
      if (!user) {
        throw new NotFoundError('User');
      }

      logger.info('Retrieved user by ID', { userId, targetUserId });

      res.json({
        success: true,
        data: user,
        message: 'User retrieved successfully',
      });
    } catch (error) {
      logger.error('Failed to get user by ID', { userId, targetUserId, error });
      throw error;
    }
  });

  /**
   * Create new user (admin only)
   * POST /api/users
   */
  createUser = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id;
    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Validate request body
    const validatedData = createUserSchema.parse(req.body);

    try {
      const user = await userService.createUser(validatedData);

      logger.info('Created new user', {
        createdUserId: user.id,
        creatingUserId: userId,
        email: user.email,
        role: user.role,
      });

      res.status(201).json({
        success: true,
        data: user,
        message: 'User created successfully',
      });
    } catch (error) {
      logger.error('Failed to create user', { userId, validatedData, error });
      throw error;
    }
  });

  /**
   * Update user
   * PUT /api/users/:id
   */
  updateUser = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id;
    const targetUserId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Validate request body
    const validatedData = updateUserSchema.parse(req.body);

    try {
      const user = await userService.updateUser(targetUserId, validatedData, userId);

      if (!user) {
        throw new NotFoundError('User');
      }

      logger.info('Updated user', {
        targetUserId,
        updatingUserId: userId,
        changes: Object.keys(validatedData),
      });

      res.json({
        success: true,
        data: user,
        message: 'User updated successfully',
      });
    } catch (error) {
      logger.error('Failed to update user', { userId, targetUserId, validatedData, error });
      throw error;
    }
  });

  /**
   * Update user profile
   * PUT /api/users/:id/profile
   */
  updateUserProfile = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id;
    const targetUserId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Validate request body
    const validatedData = updateProfileSchema.parse(req.body);

    try {
      const profile = await userService.updateUserProfile(targetUserId, validatedData, userId);

      if (!profile) {
        throw new NotFoundError('User profile');
      }

      logger.info('Updated user profile', {
        targetUserId,
        updatingUserId: userId,
        changes: Object.keys(validatedData),
      });

      res.json({
        success: true,
        data: profile,
        message: 'Profile updated successfully',
      });
    } catch (error) {
      logger.error('Failed to update user profile', { userId, targetUserId, validatedData, error });
      throw error;
    }
  });

  /**
   * Delete user (admin only)
   * DELETE /api/users/:id
   */
  deleteUser = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id;
    const targetUserId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    try {
      const deleted = await userService.deleteUser(targetUserId, userId);

      if (!deleted) {
        throw new NotFoundError('User');
      }

      logger.info('Deleted user', { targetUserId, deletingUserId: userId });

      res.json({
        success: true,
        message: 'User deleted successfully',
      });
    } catch (error) {
      logger.error('Failed to delete user', { userId, targetUserId, error });
      throw error;
    }
  });

  /**
   * Get user statistics
   * GET /api/users/:id/stats
   */
  getUserStats = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id;
    const targetUserId = req.params.id!;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    try {
      const stats = await userService.getUserStats(targetUserId, userId);

      logger.info('Retrieved user statistics', { targetUserId, requestingUserId: userId });

      res.json({
        success: true,
        data: stats,
        message: 'User statistics retrieved successfully',
      });
    } catch (error) {
      logger.error('Failed to get user stats', { userId, targetUserId, error });
      throw error;
    }
  });

  /**
   * Search users
   * GET /api/users/search
   */
  searchUsers = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id;
    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Validate query parameters
    const { q, limit } = searchUsersSchema.parse(req.query);

    try {
      const users = await userService.searchUsers(q, userId, limit);

      logger.info('Searched users', {
        requestingUserId: userId,
        query: q,
        results: users.length,
      });

      res.json({
        success: true,
        data: users,
        message: 'Users found successfully',
      });
    } catch (error) {
      logger.error('Failed to search users', { userId, query: q, error });
      throw error;
    }
  });

  /**
   * Change password
   * POST /api/users/me/change-password
   */
  changePassword = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id;
    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Validate request body using auth schema
    const { currentPassword, newPassword } = req.body;

    if (!currentPassword || !newPassword) {
      throw new ValidationError('Current password and new password are required');
    }

    try {
      await authService.changePassword(userId, currentPassword, newPassword);

      logger.info('User changed password', { userId });

      res.json({
        success: true,
        message: 'Password changed successfully',
      });
    } catch (error) {
      logger.error('Failed to change password', { userId, error });
      throw error;
    }
  });

  /**
   * Upload user avatar
   * POST /api/users/me/avatar
   */
  uploadAvatar = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id;
    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    try {
      // Check if file was uploaded (multer adds file to request)
      const file = (req as any).file as Express.Multer.File | undefined;

      let avatarUrl: string;

      if (file) {
        // File was uploaded via multer
        avatarUrl = getAvatarUrl(file.filename);

        // Get old avatar to delete
        const oldProfile = await prisma.userProfile.findUnique({
          where: { userId },
          select: { avatar: true }
        });

        // Delete old avatar file if it exists
        if (oldProfile?.avatar) {
          await deleteOldAvatar(oldProfile.avatar);
        }

        logger.info('Avatar file uploaded', { userId, filename: file.filename });
      } else if (req.body.avatarUrl) {
        // Avatar URL provided directly (e.g., from external source)
        avatarUrl = req.body.avatarUrl;
      } else {
        throw new ValidationError('No avatar file or URL provided');
      }

      // Update profile with new avatar URL
      const profile = await userService.updateUserProfile(
        userId,
        { avatar: avatarUrl },
        userId
      );

      // Log activity
      await prisma.activityLog.create({
        data: {
          action: 'AVATAR_UPDATED',
          entity: 'user',
          entityId: userId,
          userId,
          details: JSON.stringify({ newAvatar: avatarUrl }),
          ipAddress: req.ip,
          userAgent: req.get('User-Agent')
        }
      });

      logger.info('Updated user avatar', { userId, avatarUrl });

      res.json({
        success: true,
        data: { avatar: profile?.avatar },
        message: 'Avatar updated successfully',
      });
    } catch (error) {
      logger.error('Failed to upload avatar', { userId, error });
      throw error;
    }
  });

  /**
   * Get user activity log
   * GET /api/users/:id/activity
   */
  getUserActivity = asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
    const userId = req.user?.id;
    // For /me/activity route, targetUserId will be undefined, use current user
    const targetUserId = req.params.id || userId;

    if (!userId) {
      throw new AppError('User not authenticated', 401);
    }

    // Only allow users to see their own activity, or admins to see any
    if (targetUserId !== userId) {
      const user = await userService.getUserById(userId);
      if (!user || (user.role !== 'SUPER_ADMIN' && user.role !== 'ADMIN')) {
        throw new AppError('Insufficient permissions', 403);
      }
    }

    try {
      const page = parseInt(req.query.page as string) || 1;
      const limit = parseInt(req.query.limit as string) || 20;
      const action = req.query.action as string | undefined;
      const entity = req.query.entity as string | undefined;
      const startDate = req.query.startDate as string | undefined;
      const endDate = req.query.endDate as string | undefined;

      // Build where clause
      const where: any = {
        userId: targetUserId
      };

      if (action) {
        where.action = { contains: action, mode: 'insensitive' };
      }

      if (entity) {
        where.entity = entity;
      }

      if (startDate || endDate) {
        where.createdAt = {};
        if (startDate) {
          where.createdAt.gte = new Date(startDate);
        }
        if (endDate) {
          where.createdAt.lte = new Date(endDate);
        }
      }

      // Calculate skip for pagination
      const skip = (page - 1) * limit;

      // Query activity logs
      const [activities, total] = await Promise.all([
        prisma.activityLog.findMany({
          where,
          skip,
          take: limit,
          orderBy: { createdAt: 'desc' },
          include: {
            dashboard: {
              select: {
                id: true,
                title: true
              }
            }
          }
        }),
        prisma.activityLog.count({ where })
      ]);

      // Format the response
      const formattedActivities = activities.map(activity => ({
        id: activity.id,
        action: activity.action,
        entity: activity.entity,
        entityId: activity.entityId,
        details: typeof activity.details === 'string'
          ? JSON.parse(activity.details)
          : activity.details,
        ipAddress: activity.ipAddress,
        userAgent: activity.userAgent,
        dashboard: activity.dashboard,
        createdAt: activity.createdAt
      }));

      const pages = Math.ceil(total / limit);

      logger.info('Retrieved user activity', {
        targetUserId,
        requestingUserId: userId,
        count: activities.length,
        total
      });

      res.json({
        success: true,
        data: {
          activities: formattedActivities,
          total,
          page,
          limit,
          pages,
          hasNext: page < pages,
          hasPrev: page > 1
        },
        message: 'User activity retrieved successfully',
      });
    } catch (error) {
      logger.error('Failed to get user activity', { userId, targetUserId, error });
      throw error;
    }
  });
}

// Create controller instance
export const userController = new UserController();

// Export individual methods for use in routes
export const {
  getAllUsers,
  getCurrentUser,
  getUserById,
  createUser,
  updateUser,
  updateUserProfile,
  deleteUser,
  getUserStats,
  searchUsers,
  changePassword,
  uploadAvatar,
  getUserActivity,
} = userController;