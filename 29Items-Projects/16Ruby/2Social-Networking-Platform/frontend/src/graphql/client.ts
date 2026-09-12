import { ApolloClient, HttpLink, InMemoryCache } from '@apollo/client';
import { setContext } from '@apollo/client/link/context';

const graphqlEndpoint =
  window.__APP_CONFIG__?.GRAPHQL_ENDPOINT ??
  import.meta.env.VITE_GRAPHQL_ENDPOINT ??
  'http://localhost:3000/graphql';

const httpLink = new HttpLink({ uri: graphqlEndpoint });

const authLink = setContext((_, { headers }) => {
  const token = window.localStorage.getItem('accessToken');
  return {
    headers: {
      ...headers,
      authorization: token ? `Bearer ${token}` : '',
    },
  };
});

export const client = new ApolloClient({
  link: authLink.concat(httpLink),
  cache: new InMemoryCache(),
});
