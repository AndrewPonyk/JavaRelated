import { apiClient as api } from './apiClient';

export interface Notification {
  id: string;
  title: string;
  message: string;
  type: 'SHARE' | 'REPORT' | 'SYSTEM' | 'ALERT' | 'INVITE' | 'COMMENT' | 'MENTION';
  read: boolean;
  actionUrl?: string;
  actionLabel?: string;
  entityType?: string;
  entityId?: string;
  userId: string;
  createdAt: string;
  readAt?: string;
}

export interface GetNotificationsParams {
  read?: boolean;
  limit?: number;
}

export interface NotificationsResponse {
  data: Notification[];
  unreadCount: number;
}

export const getNotifications = async (params?: GetNotificationsParams) => {
  const response = await api.get<NotificationsResponse>('/notifications', { params });
  return response.data;
};

export const getNotification = async (id: string) => {
  const response = await api.get<{ data: Notification }>(`/notifications/${id}`);
  return response.data;
};

export const markAsRead = async (id: string) => {
  const response = await api.put<{ data: Notification }>(`/notifications/${id}/read`);
  return response.data;
};

export const markAllAsRead = async () => {
  const response = await api.put<{ message: string }>('/notifications/read-all');
  return response.data;
};

export const deleteNotification = async (id: string) => {
  await api.delete(`/notifications/${id}`);
};

export const deleteReadNotifications = async () => {
  const response = await api.delete<{ message: string }>('/notifications/read');
  return response.data;
};
