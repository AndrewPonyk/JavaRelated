import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { createDashboardController } from '@/controllers/dashboard/dashboardController.js';
import { dashboardService } from '@/services/dashboard/dashboardService.js';
import { extendedCacheService } from '@/services/cache/cacheService.js';
import { authenticate, optionalAuthenticate } from '@/middleware/auth/authMiddleware.js';
import { csrfProtection } from '@/middleware/security/csrfMiddleware.js';
const router = Router();
const dashboardController = createDashboardController(dashboardService, extendedCacheService);
const dashboardRateLimit = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 200,
    message: {
        success: false,
        error: {
            code: 'RATE_LIMIT_EXCEEDED',
            message: 'Too many dashboard requests, please try again later',
            statusCode: 429,
        },
    },
    standardHeaders: true,
    legacyHeaders: false,
});
router.use(dashboardRateLimit);
router.get('/public', optionalAuthenticate, async (req, res, next) => {
    req.query.isPublic = 'true';
    dashboardController.getDashboards(req, res, next);
});
router.get('/templates', optionalAuthenticate, async (req, res, next) => {
    req.query.isTemplate = 'true';
    dashboardController.getDashboards(req, res, next);
});
router.use(authenticate);
router.get('/', dashboardController.getDashboards);
router.post('/', csrfProtection, dashboardController.createDashboard);
router.get('/:id', dashboardController.getDashboard);
router.put('/:id', csrfProtection, dashboardController.updateDashboard);
router.delete('/:id', csrfProtection, dashboardController.deleteDashboard);
router.post('/:id/share', csrfProtection, dashboardController.shareDashboard);
router.get('/:id/shares', async (req, res, next) => {
    res.json({
        success: true,
        data: [],
        message: 'Dashboard shares retrieved successfully',
    });
});
router.delete('/:id/shares/:userId', async (req, res, next) => {
    res.json({
        success: true,
        message: 'Dashboard share removed successfully',
    });
});
router.get('/:id/analytics', dashboardController.getDashboardAnalytics);
router.post('/:id/analytics/track', async (req, res, next) => {
    res.json({
        success: true,
        message: 'Analytics tracked successfully',
    });
});
router.post('/:id/export', async (req, res, next) => {
    const { format = 'pdf' } = req.body;
    res.json({
        success: true,
        data: {
            exportId: 'export-' + Date.now(),
            format,
            status: 'pending',
        },
        message: 'Export initiated successfully',
    });
});
router.get('/:id/export/:exportId', async (req, res, next) => {
    const { exportId } = req.params;
    res.json({
        success: true,
        data: {
            exportId,
            status: 'completed',
            downloadUrl: `/api/exports/${exportId}/download`,
        },
        message: 'Export status retrieved successfully',
    });
});
router.post('/:id/duplicate', async (req, res, next) => {
    const { title } = req.body;
    res.json({
        success: true,
        data: {
            id: 'new-dashboard-' + Date.now(),
            title: title || 'Copy of Dashboard',
        },
        message: 'Dashboard duplicated successfully',
    });
});
router.get('/:id/versions', async (req, res, next) => {
    res.json({
        success: true,
        data: [],
        message: 'Dashboard versions retrieved successfully',
    });
});
router.post('/:id/versions/:versionId/restore', async (req, res, next) => {
    res.json({
        success: true,
        message: 'Dashboard restored to previous version successfully',
    });
});
router.post('/:id/embed', async (req, res, next) => {
    res.json({
        success: true,
        data: {
            embedToken: 'embed-token-' + Date.now(),
            expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
            embedUrl: `/embed/dashboard/${req.params.id}`,
        },
        message: 'Embed token generated successfully',
    });
});
router.get('/:id/comments', async (req, res, next) => {
    res.json({
        success: true,
        data: [],
        message: 'Dashboard comments retrieved successfully',
    });
});
router.post('/:id/comments', async (req, res, next) => {
    res.json({
        success: true,
        data: {
            id: 'comment-' + Date.now(),
            content: req.body.content,
            createdAt: new Date().toISOString(),
        },
        message: 'Comment added successfully',
    });
});
export default router;
//# sourceMappingURL=dashboard.js.map