import { useMutation } from '@apollo/client';
import { useState } from 'react';

import { DASHBOARD_QUERY, FOLLOW_USER } from '../graphql/operations';
import type { User } from '../types/graphql';

type UsersPanelProps = {
  currentUser?: User | null;
  users: User[];
};

export function UsersPanel({ currentUser, users }: UsersPanelProps) {
  const [error, setError] = useState('');
  const [followUser, state] = useMutation(FOLLOW_USER, {
    refetchQueries: [DASHBOARD_QUERY],
  });

  const visibleUsers = users.filter((user) => user.id !== currentUser?.id);

  const follow = async (followeeId: string) => {
    try {
      setError('');
      const result = await followUser({ variables: { followeeId } });
      const errors = result.data?.followUser?.errors ?? [];
      if (errors.length) {
        setError(errors.join(', '));
      }
    } catch (mutationError) {
      setError(mutationError instanceof Error ? mutationError.message : 'Unable to follow user');
    }
  };

  return (
    <section className="side-section">
      <h2>People</h2>
      {visibleUsers.length ? (
        <ul className="compact-list">
          {visibleUsers.map((user) => (
            <li key={user.id}>
              <span>@{user.username}</span>
              <button
                type="button"
                disabled={state.loading}
                onClick={() => void follow(user.id)}
              >
                Follow
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="state-message">No other users yet.</p>
      )}
      {error ? <p className="inline-error">{error}</p> : null}
    </section>
  );
}
