import { Router } from 'express';
import { authenticate } from '@/middleware/auth/authMiddleware.js';
import {
  getNotifications,
  getNotification,
  markAsRead,
  markAllAsRead,
  deleteNotification,
  deleteReadNotifications,
} from '@/controllers/notificationController.js';

const router = Router();

// All routes require authentication
router.use(authenticate);

router.get('/', getNotifications);
router.put('/read-all', markAllAsRead);
router.delete('/read', deleteReadNotifications);
router.get('/:id', getNotification);
router.put('/:id/read', markAsRead);
router.delete('/:id', deleteNotification);

export default router;
