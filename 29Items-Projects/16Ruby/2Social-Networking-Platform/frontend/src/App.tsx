import { useMemo, useState } from 'react';
import { useApolloClient, useQuery } from '@apollo/client';

import { AuthPanel } from './components/AuthPanel';
import { Composer } from './components/Composer';
import { Feed } from './components/Feed';
import { MessagesPanel } from './components/MessagesPanel';
import { NotificationsPanel } from './components/NotificationsPanel';
import { SearchPanel } from './components/SearchPanel';
import { UsersPanel } from './components/UsersPanel';
import { DASHBOARD_QUERY } from './graphql/operations';
import type { AppNotification, FeedPost, Message, User } from './types/graphql';

type DashboardResult = {
  me: User | null;
  users: User[];
  feed: FeedPost[];
  messages: Message[];
  notifications: AppNotification[];
};

export function App() {
  const apollo = useApolloClient();
  const [token, setToken] = useState(() => window.localStorage.getItem('accessToken'));
  const isAuthenticated = Boolean(token);
  const dashboard = useQuery<DashboardResult>(DASHBOARD_QUERY, {
    skip: !isAuthenticated,
    fetchPolicy: 'cache-and-network',
  });

  const data = useMemo(
    () => ({
      me: dashboard.data?.me ?? null,
      users: dashboard.data?.users ?? [],
      feed: dashboard.data?.feed ?? [],
      messages: dashboard.data?.messages ?? [],
      notifications: dashboard.data?.notifications ?? [],
    }),
    [dashboard.data],
  );

  const signOut = async () => {
    window.localStorage.removeItem('accessToken');
    window.localStorage.removeItem('refreshToken');
    setToken(null);
    await apollo.clearStore();
  };

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <h1>Social Networking Platform</h1>
          {data.me ? <p>@{data.me.username}</p> : null}
        </div>
        {isAuthenticated ? (
          <button type="button" onClick={() => void signOut()}>
            Sign out
          </button>
        ) : null}
      </header>

      {!isAuthenticated ? (
        <AuthPanel
          onAuthenticated={(session) => {
            window.localStorage.setItem('accessToken', session.accessToken);
            window.localStorage.setItem('refreshToken', session.refreshToken);
            setToken(session.accessToken);
            void dashboard.refetch();
          }}
        />
      ) : (
        <div className="dashboard-grid">
          <section className="feed-panel" aria-label="Home feed">
            <Composer />
            {dashboard.error ? <p className="state-message" role="alert">Unable to load dashboard.</p> : null}
            <Feed posts={data.feed} loading={dashboard.loading} />
          </section>

          <aside className="sidebar">
            <UsersPanel currentUser={data.me} users={data.users} />
            <SearchPanel />
            <MessagesPanel currentUser={data.me} users={data.users} messages={data.messages} />
            <NotificationsPanel notifications={data.notifications} />
          </aside>
        </div>
      )}
    </main>
  );
}
