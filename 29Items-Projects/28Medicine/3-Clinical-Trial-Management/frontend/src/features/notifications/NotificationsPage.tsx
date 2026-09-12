// In-app notifications: list the current user's notifications and mark them read.
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/api/client";
import { AsyncBoundary } from "@/components/AsyncBoundary";
import type { Notification, Paginated } from "@/types";

export function NotificationsPage() {
  const qc = useQueryClient();
  const notifications = useQuery({
    queryKey: ["notifications"],
    queryFn: () => api.get<Paginated<Notification>>("/notifications/"),
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ["notifications"] });

  const markRead = useMutation({
    mutationFn: (id: number) => api.post(`/notifications/${id}/mark-read/`),
    onSuccess: invalidate,
  });
  const markAll = useMutation({
    mutationFn: () => api.post("/notifications/mark-all-read/"),
    onSuccess: invalidate,
  });

  const rows = notifications.data?.results ?? [];

  return (
    <section>
      <div className="row-between">
        <h2>Notifications</h2>
        <button onClick={() => markAll.mutate()} disabled={markAll.isPending}>
          Mark all read
        </button>
      </div>

      <AsyncBoundary
        isLoading={notifications.isLoading}
        isError={notifications.isError}
        error={notifications.error}
        isEmpty={rows.length === 0}
        emptyMessage="No notifications."
        onRetry={() => notifications.refetch()}
      >
        <ul className="notification-list">
          {rows.map((n) => (
            <li key={n.id} className={n.read ? "read" : "unread"}>
              <div>
                <strong>{n.subject}</strong>
                {n.body && <p className="muted">{n.body}</p>}
                <span className="tag">{n.kind}</span>
              </div>
              {!n.read && <button onClick={() => markRead.mutate(n.id)}>Mark read</button>}
            </li>
          ))}
        </ul>
      </AsyncBoundary>
    </section>
  );
}
