import { Request, Response } from 'express';
import { asyncHandler } from '@/utils/asyncHandler.js';
import { prisma } from '@/config/database.js';

// GET /api/notifications
export const getNotifications = asyncHandler(async (req: Request, res: Response): Promise<void> => {
  const userId = (req as any).user!.id;
  const { read, limit = 50 } = req.query;

  const where: any = { userId };
  if (read !== undefined) {
    where.read = read === 'true';
  }

  const notifications = await prisma.notification.findMany({
    where,
    orderBy: { createdAt: 'desc' },
    take: Number(limit),
  });

  const unreadCount = await prisma.notification.count({
    where: { userId, read: false },
  });

  res.json({ data: notifications, unreadCount });
});

// GET /api/notifications/:id
export const getNotification = asyncHandler(async (req: Request, res: Response): Promise<void> => {
  const userId = (req as any).user!.id;
  const { id } = req.params;

  const notification = await prisma.notification.findFirst({
    where: { id, userId },
  });

  if (!notification) {
    res.status(404).json({ error: 'Notification not found' });
    return;
  }

  res.json({ data: notification });
});

// PUT /api/notifications/:id/read
export const markAsRead = asyncHandler(async (req: Request, res: Response): Promise<void> => {
  const userId = (req as any).user!.id;
  const { id } = req.params;

  const existing = await prisma.notification.findFirst({
    where: { id, userId },
  });

  if (!existing) {
    res.status(404).json({ error: 'Notification not found' });
    return;
  }

  const notification = await prisma.notification.update({
    where: { id },
    data: { read: true, readAt: new Date() },
  });

  res.json({ data: notification });
});

// PUT /api/notifications/read-all
export const markAllAsRead = asyncHandler(async (req: Request, res: Response): Promise<void> => {
  const userId = (req as any).user!.id;

  await prisma.notification.updateMany({
    where: { userId, read: false },
    data: { read: true, readAt: new Date() },
  });

  res.json({ message: 'All notifications marked as read' });
});

// DELETE /api/notifications/:id
export const deleteNotification = asyncHandler(async (req: Request, res: Response): Promise<void> => {
  const userId = (req as any).user!.id;
  const { id } = req.params;

  const existing = await prisma.notification.findFirst({
    where: { id, userId },
  });

  if (!existing) {
    res.status(404).json({ error: 'Notification not found' });
    return;
  }

  await prisma.notification.delete({ where: { id } });

  res.status(204).send();
});

// DELETE /api/notifications (bulk delete read notifications)
export const deleteReadNotifications = asyncHandler(async (req: Request, res: Response): Promise<void> => {
  const userId = (req as any).user!.id;

  const result = await prisma.notification.deleteMany({
    where: { userId, read: true },
  });

  res.json({ message: `Deleted ${result.count} notifications` });
});

// Utility to create a notification (used internally)
export const createNotification = async (
  userId: string,
  type: 'SHARE' | 'REPORT' | 'SYSTEM' | 'ALERT' | 'INVITE' | 'COMMENT' | 'MENTION',
  title: string,
  message: string,
  options?: {
    actionUrl?: string;
    actionLabel?: string;
    entityType?: string;
    entityId?: string;
  }
) => {
  return prisma.notification.create({
    data: {
      userId,
      type,
      title,
      message,
      ...options,
    },
  });
};
