import { useMutation } from '@apollo/client';
import { useState } from 'react';

import { DASHBOARD_QUERY, MARK_NOTIFICATION_READ } from '../graphql/operations';
import type { AppNotification } from '../types/graphql';

type NotificationsPanelProps = {
  notifications: AppNotification[];
};

export function NotificationsPanel({ notifications }: NotificationsPanelProps) {
  const [error, setError] = useState('');
  const [markRead, state] = useMutation(MARK_NOTIFICATION_READ, {
    refetchQueries: [DASHBOARD_QUERY],
  });

  const markNotificationRead = async (id: string) => {
    try {
      setError('');
      await markRead({ variables: { id } });
    } catch (mutationError) {
      setError(mutationError instanceof Error ? mutationError.message : 'Unable to update notification');
    }
  };

  return (
    <section className="side-section">
      <h2>Notifications</h2>
      {notifications.length ? (
        <ul className="compact-list">
          {notifications.map((notification) => (
            <li key={notification.id}>
              <span>
                {notification.kind} {notification.readAt ? '' : '(new)'}
              </span>
              {!notification.readAt ? (
                <button
                  type="button"
                  disabled={state.loading}
                  onClick={() => void markNotificationRead(notification.id)}
                >
                  Read
                </button>
              ) : null}
            </li>
          ))}
        </ul>
      ) : (
        <p className="state-message">No notifications.</p>
      )}
      {error ? <p className="inline-error">{error}</p> : null}
    </section>
  );
}
