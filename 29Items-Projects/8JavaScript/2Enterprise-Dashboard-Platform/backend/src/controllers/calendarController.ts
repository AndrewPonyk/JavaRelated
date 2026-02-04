import { Request, Response } from 'express';
import { asyncHandler } from '@/utils/asyncHandler.js';
import { prisma } from '@/config/database.js';
import { z } from 'zod';

// Validation schemas
const createEventSchema = z.object({
  title: z.string().min(1).max(200),
  description: z.string().max(1000).optional(),
  startTime: z.string().datetime(),
  endTime: z.string().datetime().optional(),
  allDay: z.boolean().optional(),
  type: z.enum(['MEETING', 'REVIEW', 'PRESENTATION', 'PLANNING', 'DEADLINE', 'REMINDER', 'OTHER']).optional(),
  color: z.string().optional(),
  location: z.string().max(200).optional(),
});

const updateEventSchema = createEventSchema.partial();

// GET /api/calendar/events
export const getEvents = asyncHandler(async (req: Request, res: Response): Promise<void> => {
  const userId = (req as any).user!.id;
  const { start, end, type } = req.query;

  const where: any = { userId };

  if (start || end) {
    where.startTime = {};
    if (start) where.startTime.gte = new Date(start as string);
    if (end) where.startTime.lte = new Date(end as string);
  }

  if (type) where.type = type;

  const events = await prisma.calendarEvent.findMany({
    where,
    orderBy: { startTime: 'asc' },
  });

  res.json({ data: events });
});

// GET /api/calendar/events/:id
export const getEvent = asyncHandler(async (req: Request, res: Response): Promise<void> => {
  const userId = (req as any).user!.id;
  const { id } = req.params;

  const event = await prisma.calendarEvent.findFirst({
    where: { id, userId },
  });

  if (!event) {
    res.status(404).json({ error: 'Event not found' });
    return;
  }

  res.json({ data: event });
});

// POST /api/calendar/events
export const createEvent = asyncHandler(async (req: Request, res: Response): Promise<void> => {
  const userId = (req as any).user!.id;
  const data = createEventSchema.parse(req.body);

  const event = await prisma.calendarEvent.create({
    data: {
      ...data,
      startTime: new Date(data.startTime),
      endTime: data.endTime ? new Date(data.endTime) : null,
      userId,
    },
  });

  res.status(201).json({ data: event });
});

// PUT /api/calendar/events/:id
export const updateEvent = asyncHandler(async (req: Request, res: Response): Promise<void> => {
  const userId = (req as any).user!.id;
  const { id } = req.params;
  const data = updateEventSchema.parse(req.body);

  const existing = await prisma.calendarEvent.findFirst({
    where: { id, userId },
  });

  if (!existing) {
    res.status(404).json({ error: 'Event not found' });
    return;
  }

  const event = await prisma.calendarEvent.update({
    where: { id },
    data: {
      ...data,
      startTime: data.startTime ? new Date(data.startTime) : undefined,
      endTime: data.endTime ? new Date(data.endTime) : undefined,
    },
  });

  res.json({ data: event });
});

// DELETE /api/calendar/events/:id
export const deleteEvent = asyncHandler(async (req: Request, res: Response): Promise<void> => {
  const userId = (req as any).user!.id;
  const { id } = req.params;

  const existing = await prisma.calendarEvent.findFirst({
    where: { id, userId },
  });

  if (!existing) {
    res.status(404).json({ error: 'Event not found' });
    return;
  }

  await prisma.calendarEvent.delete({ where: { id } });

  res.status(204).send();
});
