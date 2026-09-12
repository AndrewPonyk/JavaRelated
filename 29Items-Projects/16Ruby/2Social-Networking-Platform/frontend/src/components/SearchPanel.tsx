import { FormEvent, useState } from 'react';
import { useLazyQuery } from '@apollo/client';

import { SEARCH_POSTS } from '../graphql/operations';
import { validateRequired } from '../lib/validation';
import type { FeedPost } from '../types/graphql';
import { Feed } from './Feed';

type SearchResult = {
  searchPosts: FeedPost[];
};

export function SearchPanel() {
  const [query, setQuery] = useState('');
  const [error, setError] = useState('');
  const [search, state] = useLazyQuery<SearchResult>(SEARCH_POSTS);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const validationError = validateRequired(query, 'Search query');
    if (validationError) {
      setError(validationError);
      return;
    }

    setError('');
    void search({ variables: { query: query.trim() } });
  };

  return (
    <section className="side-section">
      <h2>Search</h2>
      <form className="search-form" onSubmit={submit}>
        <input aria-label="Search posts" value={query} onChange={(event) => setQuery(event.target.value)} />
        <button type="submit">Search</button>
      </form>
      {error ? <p className="inline-error">{error}</p> : null}
      {state.error ? <p className="inline-error">Search failed.</p> : null}
      {state.called ? <Feed posts={state.data?.searchPosts ?? []} loading={state.loading} /> : null}
    </section>
  );
}
