import { z } from 'zod';
import { userService } from '@/services/user/userService.js';
import { authService } from '@/middleware/auth/authMiddleware.js';
import { logger } from '@/utils/logger.js';
import { AppError, ValidationError, NotFoundError } from '@/utils/errors.js';
import { asyncHandler } from '@/utils/asyncHandler.js';
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
    getAllUsers = asyncHandler(async (req, res) => {
        const userId = req.user?.id;
        if (!userId) {
            throw new AppError('User not authenticated', 401);
        }
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
        }
        catch (error) {
            logger.error('Failed to get all users', { userId, queryParams, error });
            throw error;
        }
    });
    getCurrentUser = asyncHandler(async (req, res) => {
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
        }
        catch (error) {
            logger.error('Failed to get current user', { userId, error });
            throw error;
        }
    });
    getUserById = asyncHandler(async (req, res) => {
        const userId = req.user?.id;
        const targetUserId = req.params.id;
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
        }
        catch (error) {
            logger.error('Failed to get user by ID', { userId, targetUserId, error });
            throw error;
        }
    });
    createUser = asyncHandler(async (req, res) => {
        const userId = req.user?.id;
        if (!userId) {
            throw new AppError('User not authenticated', 401);
        }
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
        }
        catch (error) {
            logger.error('Failed to create user', { userId, validatedData, error });
            throw error;
        }
    });
    updateUser = asyncHandler(async (req, res) => {
        const userId = req.user?.id;
        const targetUserId = req.params.id;
        if (!userId) {
            throw new AppError('User not authenticated', 401);
        }
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
        }
        catch (error) {
            logger.error('Failed to update user', { userId, targetUserId, validatedData, error });
            throw error;
        }
    });
    updateUserProfile = asyncHandler(async (req, res) => {
        const userId = req.user?.id;
        const targetUserId = req.params.id;
        if (!userId) {
            throw new AppError('User not authenticated', 401);
        }
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
        }
        catch (error) {
            logger.error('Failed to update user profile', { userId, targetUserId, validatedData, error });
            throw error;
        }
    });
    deleteUser = asyncHandler(async (req, res) => {
        const userId = req.user?.id;
        const targetUserId = req.params.id;
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
        }
        catch (error) {
            logger.error('Failed to delete user', { userId, targetUserId, error });
            throw error;
        }
    });
    getUserStats = asyncHandler(async (req, res) => {
        const userId = req.user?.id;
        const targetUserId = req.params.id;
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
        }
        catch (error) {
            logger.error('Failed to get user stats', { userId, targetUserId, error });
            throw error;
        }
    });
    searchUsers = asyncHandler(async (req, res) => {
        const userId = req.user?.id;
        if (!userId) {
            throw new AppError('User not authenticated', 401);
        }
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
        }
        catch (error) {
            logger.error('Failed to search users', { userId, query: q, error });
            throw error;
        }
    });
    changePassword = asyncHandler(async (req, res) => {
        const userId = req.user?.id;
        if (!userId) {
            throw new AppError('User not authenticated', 401);
        }
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
        }
        catch (error) {
            logger.error('Failed to change password', { userId, error });
            throw error;
        }
    });
    uploadAvatar = asyncHandler(async (req, res) => {
        const userId = req.user?.id;
        if (!userId) {
            throw new AppError('User not authenticated', 401);
        }
        try {
            const avatarUrl = req.body.avatarUrl;
            if (!avatarUrl) {
                throw new ValidationError('Avatar URL is required');
            }
            const profile = await userService.updateUserProfile(userId, { avatar: avatarUrl }, userId);
            logger.info('Updated user avatar', { userId, avatarUrl });
            res.json({
                success: true,
                data: { avatar: profile?.avatar },
                message: 'Avatar updated successfully',
            });
        }
        catch (error) {
            logger.error('Failed to upload avatar', { userId, error });
            throw error;
        }
    });
    getUserActivity = asyncHandler(async (req, res) => {
        const userId = req.user?.id;
        const targetUserId = req.params.id;
        if (!userId) {
            throw new AppError('User not authenticated', 401);
        }
        if (targetUserId !== userId) {
            const user = await userService.getUserById(userId);
            if (!user || (user.role !== 'SUPER_ADMIN' && user.role !== 'ADMIN')) {
                throw new AppError('Insufficient permissions', 403);
            }
        }
        try {
            const page = parseInt(req.query.page) || 1;
            const limit = parseInt(req.query.limit) || 20;
            const activity = {
                data: [],
                total: 0,
                page,
                limit,
                pages: 0,
            };
            logger.info('Retrieved user activity', { targetUserId, requestingUserId: userId });
            res.json({
                success: true,
                data: activity,
                message: 'User activity retrieved successfully',
            });
        }
        catch (error) {
            logger.error('Failed to get user activity', { userId, targetUserId, error });
            throw error;
        }
    });
}
export const userController = new UserController();
export const { getAllUsers, getCurrentUser, getUserById, createUser, updateUser, updateUserProfile, deleteUser, getUserStats, searchUsers, changePassword, uploadAvatar, getUserActivity, } = userController;
//# sourceMappingURL=userController.js.map