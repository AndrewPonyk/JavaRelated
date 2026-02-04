import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { userController } from '@/controllers/user/userController.js';
import { authenticate, authorize } from '@/middleware/auth/authMiddleware.js';
const router = Router();
const userRateLimit = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 100,
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
router.use(authenticate);
router.use(userRateLimit);
router.get('/search', userController.searchUsers);
router.get('/me', userController.getCurrentUser);
router.post('/me/change-password', userController.changePassword);
router.post('/me/avatar', userController.uploadAvatar);
router.get('/me/activity', userController.getUserActivity);
router.get('/', authorize('SUPER_ADMIN', 'ADMIN'), userController.getAllUsers);
router.post('/', authorize('SUPER_ADMIN', 'ADMIN'), userController.createUser);
router.get('/:id', userController.getUserById);
router.put('/:id', userController.updateUser);
router.delete('/:id', authorize('SUPER_ADMIN', 'ADMIN'), userController.deleteUser);
router.put('/:id/profile', userController.updateUserProfile);
router.get('/:id/stats', userController.getUserStats);
router.get('/:id/activity', userController.getUserActivity);
export default router;
//# sourceMappingURL=users.js.map