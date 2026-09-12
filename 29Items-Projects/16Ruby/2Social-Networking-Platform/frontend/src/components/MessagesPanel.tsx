import { FormEvent, useState } from 'react';
import { useMutation } from '@apollo/client';

import { DASHBOARD_QUERY, SEND_MESSAGE } from '../graphql/operations';
import { validateRequired } from '../lib/validation';
import type { Message, User } from '../types/graphql';

type MessagesPanelProps = {
  currentUser?: User | null;
  users: User[];
  messages: Message[];
};

export function MessagesPanel({ currentUser, users, messages }: MessagesPanelProps) {
  const [recipientId, setRecipientId] = useState('');
  const [body, setBody] = useState('');
  const [error, setError] = useState('');
  const [sendMessage, state] = useMutation(SEND_MESSAGE, {
    refetchQueries: [DASHBOARD_QUERY],
  });

  const recipients = users.filter((user) => user.id !== currentUser?.id);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const validationError = validateRequired(recipientId, 'Recipient') || validateRequired(body, 'Message');
    if (validationError) {
      setError(validationError);
      return;
    }

    try {
      const result = await sendMessage({ variables: { recipientId, body: body.trim() } });
      const errors = result.data?.sendMessage?.errors ?? [];
      if (errors.length) {
        setError(errors.join(', '));
        return;
      }
    } catch (mutationError) {
      setError(mutationError instanceof Error ? mutationError.message : 'Unable to send message');
      return;
    }

    setBody('');
    setError('');
  };

  return (
    <section className="side-section">
      <h2>Messages</h2>
      <form className="stack" onSubmit={submit}>
        <select value={recipientId} onChange={(event) => setRecipientId(event.target.value)} aria-label="Recipient">
          <option value="">Choose recipient</option>
          {recipients.map((user) => (
            <option key={user.id} value={user.id}>
              @{user.username}
            </option>
          ))}
        </select>
        <textarea
          aria-label="Message body"
          maxLength={5000}
          value={body}
          onChange={(event) => setBody(event.target.value)}
        />
        <button type="submit" disabled={state.loading}>
          {state.loading ? 'Sending...' : 'Send'}
        </button>
      </form>
      {error ? <p className="inline-error">{error}</p> : null}

      <ul className="compact-list">
        {messages.map((message) => (
          <li key={message.id}>
            <span>
              @{message.sender.username} to @{message.recipient.username}: {message.body}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
