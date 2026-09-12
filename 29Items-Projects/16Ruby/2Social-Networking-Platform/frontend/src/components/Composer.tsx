import { FormEvent, useState } from 'react';
import { useMutation } from '@apollo/client';

import { CREATE_POST, DASHBOARD_QUERY } from '../graphql/operations';
import { validateRequired } from '../lib/validation';

export function Composer() {
  const [body, setBody] = useState('');
  const [visibility, setVisibility] = useState('public');
  const [error, setError] = useState('');
  const [createPost, state] = useMutation(CREATE_POST, {
    refetchQueries: [DASHBOARD_QUERY],
  });

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const validationError = validateRequired(body, 'Post body');
    if (validationError) {
      setError(validationError);
      return;
    }

    try {
      const result = await createPost({ variables: { body: body.trim(), visibility } });
      const errors = result.data?.createPost?.errors ?? [];
      if (errors.length) {
        setError(errors.join(', '));
        return;
      }
    } catch (mutationError) {
      setError(mutationError instanceof Error ? mutationError.message : 'Unable to create post');
      return;
    }

    setBody('');
    setError('');
  };

  return (
    <form className="composer" onSubmit={submit}>
      <textarea
        aria-label="Post body"
        value={body}
        maxLength={2000}
        onChange={(event) => setBody(event.target.value)}
      />
      <div className="form-row">
        <select value={visibility} onChange={(event) => setVisibility(event.target.value)} aria-label="Visibility">
          <option value="public">Public</option>
          <option value="followers">Followers</option>
          <option value="private">Private</option>
        </select>
        <button type="submit" disabled={state.loading}>
          {state.loading ? 'Posting...' : 'Post'}
        </button>
      </div>
      {error ? <p className="inline-error">{error}</p> : null}
    </form>
  );
}
