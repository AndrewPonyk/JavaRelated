import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { authController } from '@/controllers/auth/authController.js';
import { authenticate, authorize, optionalAuthenticate } from '@/middleware/auth/authMiddleware.js';
import { generateCSRFToken, refreshCSRFToken, conditionalCSRFProtection } from '@/middleware/security/csrfMiddleware.js';
import { config } from '@/config/environment.js';

const router = Router();

// Rate limiting for auth endpoints
const authRateLimit = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 10, // Limit each IP to 10 requests per windowMs
  message: {
    success: false,
    error: {
      code: 'RATE_LIMIT_EXCEEDED',
      message: 'Too many authentication attempts, please try again later',
      statusCode: 429,
    },
  },
  standardHeaders: true,
  legacyHeaders: false,
});

const strictAuthRateLimit = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 5, // Stricter limit for sensitive operations
  message: {
    success: false,
    error: {
      code: 'RATE_LIMIT_EXCEEDED',
      message: 'Too many attempts, please try again later',
      statusCode: 429,
    },
  },
  standardHeaders: true,
  legacyHeaders: false,
});

// Public routes (no authentication required)

/**
 * @route   POST /api/auth/register
 * @desc    Register a new user
 * @access  Public
 */
router.post('/register', authRateLimit, conditionalCSRFProtection, authController.register);

/**
 * @route   POST /api/auth/login
 * @desc    Authenticate user and get token
 * @access  Public
 */
router.post('/login', authRateLimit, conditionalCSRFProtection, authController.login);

/**
 * @route   POST /api/auth/refresh
 * @desc    Refresh access token using refresh token
 * @access  Public
 */
router.post('/refresh', authRateLimit, authController.refresh);

/**
 * @route   POST /api/auth/forgot-password
 * @desc    Initiate password reset process
 * @access  Public
 */
router.post('/forgot-password', strictAuthRateLimit, conditionalCSRFProtection, authController.forgotPassword);

/**
 * @route   POST /api/auth/reset-password
 * @desc    Complete password reset with token
 * @access  Public
 */
router.post('/reset-password', strictAuthRateLimit, conditionalCSRFProtection, authController.resetPassword);

// Protected routes (authentication required)

/**
 * @route   POST /api/auth/logout
 * @desc    Logout user and invalidate token
 * @access  Private
 */
router.post('/logout', authenticate, conditionalCSRFProtection, authController.logout);

/**
 * @route   POST /api/auth/verify
 * @desc    Verify if current token is valid
 * @access  Private
 */
router.post('/verify', authenticate, authController.verifyToken);

/**
 * @route   GET /api/auth/me
 * @desc    Get current user profile
 * @access  Private
 */
router.get('/me', authenticate, authController.getCurrentUser);

/**
 * @route   POST /api/auth/change-password
 * @desc    Change user password
 * @access  Private
 */
router.post('/change-password', authenticate, strictAuthRateLimit, conditionalCSRFProtection, authController.changePassword);

// CSRF protection routes
/**
 * @route   GET /api/auth/csrf-token
 * @desc    Generate CSRF token for client requests
 * @access  Public
 */
router.get('/csrf-token', generateCSRFToken);

/**
 * @route   POST /api/auth/csrf-token/refresh
 * @desc    Refresh CSRF token
 * @access  Public
 */
router.post('/csrf-token/refresh', refreshCSRFToken);

// Health check route for auth service
/**
 * @route   GET /api/auth/health
 * @desc    Check auth service health
 * @access  Public
 */
router.get('/health', (req, res) => {
  res.json({
    success: true,
    data: {
      service: 'auth',
      status: 'healthy',
      timestamp: new Date().toISOString(),
      environment: config.app.env,
    },
    message: 'Auth service is healthy',
  });
});

export default router;