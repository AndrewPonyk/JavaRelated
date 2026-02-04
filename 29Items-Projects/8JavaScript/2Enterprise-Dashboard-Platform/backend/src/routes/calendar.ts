import { Router } from 'express';
import { authenticate } from '@/middleware/auth/authMiddleware.js';
import {
  getEvents,
  getEvent,
  createEvent,
  updateEvent,
  deleteEvent,
} from '@/controllers/calendarController.js';

const router = Router();

// All routes require authentication
router.use(authenticate);

router.get('/events', getEvents);
router.get('/events/:id', getEvent);
router.post('/events', createEvent);
router.put('/events/:id', updateEvent);
router.delete('/events/:id', deleteEvent);

export default router;
