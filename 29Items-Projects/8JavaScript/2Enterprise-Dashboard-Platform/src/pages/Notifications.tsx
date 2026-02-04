import React, { useState, useEffect } from 'react';
import { Bell, Check, CheckCheck, Trash2, Settings, Filter, RefreshCw } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { cn } from '@/utils/cn';
import { useToastHelpers } from '@/components/ui/Toaster';
import {
  getNotifications,
  markAsRead,
  markAllAsRead,
  deleteNotification,
  deleteReadNotifications,
  Notification,
} from '@/services/api/notificationApi';

export const Notifications: React.FC = () => {
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [filter, setFilter] = useState<'all' | 'unread'>('all');
  const [isLoading, setIsLoading] = useState(true);
  const { success: showSuccess, error: showError } = useToastHelpers();

  useEffect(() => {
    loadNotifications();
  }, []);

  const loadNotifications = async () => {
    setIsLoading(true);
    try {
      const result = await getNotifications({ limit: 100 });
      setNotifications(result.data);
      setUnreadCount(result.unreadCount);
    } catch (err) {
      showError('Failed to load', 'Could not load notifications');
    } finally {
      setIsLoading(false);
    }
  };

  const filteredNotifications = filter === 'unread'
    ? notifications.filter(n => !n.read)
    : notifications;

  const handleMarkAsRead = async (id: string) => {
    try {
      await markAsRead(id);
      setNotifications(notifications.map(n => n.id === id ? { ...n, read: true } : n));
      setUnreadCount(prev => Math.max(0, prev - 1));
    } catch (err) {
      showError('Error', 'Could not mark as read');
    }
  };

  const handleMarkAllAsRead = async () => {
    try {
      await markAllAsRead();
      setNotifications(notifications.map(n => ({ ...n, read: true })));
      setUnreadCount(0);
      showSuccess('Done', 'All notifications marked as read');
    } catch (err) {
      showError('Error', 'Could not mark all as read');
    }
  };

  const handleDeleteNotification = async (id: string) => {
    try {
      await deleteNotification(id);
      const removed = notifications.find(n => n.id === id);
      setNotifications(notifications.filter(n => n.id !== id));
      if (removed && !removed.read) {
        setUnreadCount(prev => Math.max(0, prev - 1));
      }
    } catch (err) {
      showError('Error', 'Could not delete notification');
    }
  };

  const handleClearRead = async () => {
    if (!confirm('Delete all read notifications?')) return;
    try {
      await deleteReadNotifications();
      setNotifications(notifications.filter(n => !n.read));
      showSuccess('Cleared', 'Read notifications have been deleted');
    } catch (err) {
      showError('Error', 'Could not delete notifications');
    }
  };

  const getTypeColor = (type: string) => {
    const colors: Record<string, string> = {
      SHARE: 'bg-blue-500',
      REPORT: 'bg-green-500',
      SYSTEM: 'bg-purple-500',
      ALERT: 'bg-yellow-500',
      INVITE: 'bg-pink-500',
      COMMENT: 'bg-orange-500',
      MENTION: 'bg-cyan-500',
    };
    return colors[type] || 'bg-gray-500';
  };

  const formatTime = (dateStr: string) => {
    const date = new Date(dateStr);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes} min ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours} hour${hours > 1 ? 's' : ''} ago`;
    const days = Math.floor(hours / 24);
    if (days < 7) return `${days} day${days > 1 ? 's' : ''} ago`;
    return date.toLocaleDateString();
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Notifications</h1>
          <p className="text-gray-600">
            {unreadCount > 0 ? `You have ${unreadCount} unread notifications` : 'All caught up!'}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={loadNotifications} disabled={isLoading}>
            <RefreshCw className={cn("h-4 w-4", isLoading && "animate-spin")} />
          </Button>
          <Button variant="outline" onClick={handleMarkAllAsRead} disabled={unreadCount === 0}>
            <CheckCheck className="mr-2 h-4 w-4" />
            Mark All Read
          </Button>
          <Button variant="outline" onClick={() => navigate('/settings')}>
            <Settings className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Filter */}
      <Card className="p-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Filter className="h-5 w-5 text-gray-400" />
            <div className="flex gap-2">
              <button
                onClick={() => setFilter('all')}
                className={cn(
                  'px-4 py-2 rounded-lg text-sm font-medium',
                  filter === 'all' ? 'bg-blue-100 text-blue-700' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                )}
              >
                All ({notifications.length})
              </button>
              <button
                onClick={() => setFilter('unread')}
                className={cn(
                  'px-4 py-2 rounded-lg text-sm font-medium',
                  filter === 'unread' ? 'bg-blue-100 text-blue-700' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                )}
              >
                Unread ({unreadCount})
              </button>
            </div>
          </div>
          {notifications.some(n => n.read) && (
            <Button variant="ghost" size="sm" onClick={handleClearRead}>
              <Trash2 className="mr-1 h-4 w-4" />
              Clear Read
            </Button>
          )}
        </div>
      </Card>

      {/* Loading State */}
      {isLoading && (
        <div className="flex items-center justify-center py-12">
          <RefreshCw className="h-8 w-8 animate-spin text-gray-400" />
        </div>
      )}

      {/* Notifications List */}
      {!isLoading && (
        <Card>
          {filteredNotifications.length > 0 ? (
            <div className="divide-y">
              {filteredNotifications.map((notification) => (
                <div
                  key={notification.id}
                  className={cn(
                    'p-4 hover:bg-gray-50 transition-colors',
                    !notification.read && 'bg-blue-50'
                  )}
                >
                  <div className="flex items-start gap-4">
                    <div className={cn('w-2 h-2 rounded-full mt-2', getTypeColor(notification.type))} />
                    <div className="flex-1">
                      <div className="flex items-start justify-between">
                        <div
                          className={cn(notification.actionUrl && 'cursor-pointer')}
                          onClick={() => notification.actionUrl && navigate(notification.actionUrl)}
                        >
                          <h3 className={cn(
                            'font-medium',
                            notification.read ? 'text-gray-700' : 'text-gray-900'
                          )}>
                            {notification.title}
                          </h3>
                          <p className="text-sm text-gray-600 mt-1">{notification.message}</p>
                          <p className="text-xs text-gray-400 mt-2">{formatTime(notification.createdAt)}</p>
                        </div>
                        <div className="flex items-center gap-2">
                          {!notification.read && (
                            <Button variant="ghost" size="sm" onClick={() => handleMarkAsRead(notification.id)}>
                              <Check className="h-4 w-4" />
                            </Button>
                          )}
                          <Button variant="ghost" size="sm" onClick={() => handleDeleteNotification(notification.id)}>
                            <Trash2 className="h-4 w-4 text-gray-400" />
                          </Button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="p-12 text-center text-gray-500">
              <Bell className="h-12 w-12 mx-auto mb-4 text-gray-300" />
              <p className="font-medium">No notifications</p>
              <p className="text-sm">You're all caught up!</p>
            </div>
          )}
        </Card>
      )}
    </div>
  );
};

export default Notifications;
