import { apiClient as api } from './apiClient';

export interface CalendarEvent {
  id: string;
  title: string;
  description?: string;
  startTime: string;
  endTime?: string;
  allDay: boolean;
  type: 'MEETING' | 'REVIEW' | 'PRESENTATION' | 'PLANNING' | 'DEADLINE' | 'REMINDER' | 'OTHER';
  color: string;
  location?: string;
  userId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateEventInput {
  title: string;
  description?: string;
  startTime: string;
  endTime?: string;
  allDay?: boolean;
  type?: CalendarEvent['type'];
  color?: string;
  location?: string;
}

export interface UpdateEventInput extends Partial<CreateEventInput> {}

export interface GetEventsParams {
  start?: string;
  end?: string;
  type?: CalendarEvent['type'];
}

export const getEvents = async (params?: GetEventsParams) => {
  const response = await api.get<{ data: CalendarEvent[] }>('/calendar/events', { params });
  return response.data;
};

export const getEvent = async (id: string) => {
  const response = await api.get<{ data: CalendarEvent }>(`/calendar/events/${id}`);
  return response.data;
};

export const createEvent = async (data: CreateEventInput) => {
  const response = await api.post<{ data: CalendarEvent }>('/calendar/events', data);
  return response.data;
};

export const updateEvent = async (id: string, data: UpdateEventInput) => {
  const response = await api.put<{ data: CalendarEvent }>(`/calendar/events/${id}`, data);
  return response.data;
};

export const deleteEvent = async (id: string) => {
  await api.delete(`/calendar/events/${id}`);
};
