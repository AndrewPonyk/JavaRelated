import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { authController } from '@/controllers/auth/authController.js';
import { authenticate } from '@/middleware/auth/authMiddleware.js';
import { generateCSRFToken, refreshCSRFToken, conditionalCSRFProtection } from '@/middleware/security/csrfMiddleware.js';
import { config } from '@/config/environment.js';
const router = Router();
const authRateLimit = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 10,
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
    windowMs: 15 * 60 * 1000,
    max: 5,
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
router.post('/register', authRateLimit, conditionalCSRFProtection, authController.register);
router.post('/login', authRateLimit, conditionalCSRFProtection, authController.login);
router.post('/refresh', authRateLimit, authController.refresh);
router.post('/forgot-password', strictAuthRateLimit, conditionalCSRFProtection, authController.forgotPassword);
router.post('/reset-password', strictAuthRateLimit, conditionalCSRFProtection, authController.resetPassword);
router.post('/logout', authenticate, conditionalCSRFProtection, authController.logout);
router.post('/verify', authenticate, authController.verifyToken);
router.get('/me', authenticate, authController.getCurrentUser);
router.post('/change-password', authenticate, strictAuthRateLimit, conditionalCSRFProtection, authController.changePassword);
router.get('/csrf-token', generateCSRFToken);
router.post('/csrf-token/refresh', refreshCSRFToken);
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
//# sourceMappingURL=auth.js.map