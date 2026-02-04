import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { userController } from '@/controllers/user/userController.js';
import { authenticate, authorize } from '@/middleware/auth/authMiddleware.js';
import { avatarUpload } from '@/config/upload.js';

const router = Router();

// Rate limiting for user operations
const userRateLimit = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100, // Limit each IP to 100 requests per windowMs
  message: {
    success: false,
    error: {
      code: 'RATE_LIMIT_EXCEEDED',
      message: 'Too many requests, please try again later',
      statusCode: 429,
    },
  },
  standardHeaders: true,
  legacyHeaders: false,
});

// Apply authentication to all routes
router.use(authenticate);

// Apply rate limiting to all routes
router.use(userRateLimit);

// User search (should come before /:id routes to avoid conflicts)
/**
 * @route   GET /api/users/search
 * @desc    Search for users by name or email
 * @access  Private
 * @params  ?q=search_query&limit=10
 */
router.get('/search', userController.searchUsers);

// Current user routes
/**
 * @route   GET /api/users/me
 * @desc    Get current user profile
 * @access  Private
 */
router.get('/me', userController.getCurrentUser);

/**
 * @route   POST /api/users/me/change-password
 * @desc    Change current user's password
 * @access  Private
 */
router.post('/me/change-password', userController.changePassword);

/**
 * @route   POST /api/users/me/avatar
 * @desc    Upload/update current user's avatar
 * @access  Private
 */
router.post('/me/avatar', avatarUpload.single('avatar'), userController.uploadAvatar);

/**
 * @route   GET /api/users/me/activity
 * @desc    Get current user's activity log
 * @access  Private
 */
router.get('/me/activity', userController.getUserActivity);

// Admin-only routes
/**
 * @route   GET /api/users
 * @desc    Get all users with pagination and filtering
 * @access  Private (Admin only)
 * @params  ?page=1&limit=10&search=query&role=USER&isActive=true&sortBy=updatedAt&sortOrder=desc
 */
router.get('/', authorize('SUPER_ADMIN', 'ADMIN'), userController.getAllUsers);

/**
 * @route   POST /api/users
 * @desc    Create a new user
 * @access  Private (Admin only)
 */
router.post('/', authorize('SUPER_ADMIN', 'ADMIN'), userController.createUser);

// Individual user routes
/**
 * @route   GET /api/users/:id
 * @desc    Get user by ID
 * @access  Private
 */
router.get('/:id', userController.getUserById);

/**
 * @route   PUT /api/users/:id
 * @desc    Update user information
 * @access  Private (Self or Admin)
 */
router.put('/:id', userController.updateUser);

/**
 * @route   DELETE /api/users/:id
 * @desc    Delete/deactivate user
 * @access  Private (Admin only)
 */
router.delete('/:id', authorize('SUPER_ADMIN', 'ADMIN'), userController.deleteUser);

/**
 * @route   PUT /api/users/:id/profile
 * @desc    Update user profile
 * @access  Private (Self or Admin)
 */
router.put('/:id/profile', userController.updateUserProfile);

/**
 * @route   GET /api/users/:id/stats
 * @desc    Get user statistics
 * @access  Private (Self or Admin)
 */
router.get('/:id/stats', userController.getUserStats);

/**
 * @route   GET /api/users/:id/activity
 * @desc    Get user activity log
 * @access  Private (Self or Admin)
 */
router.get('/:id/activity', userController.getUserActivity);

export default router;