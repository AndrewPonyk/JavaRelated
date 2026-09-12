import { gql } from '@apollo/client';

export const SESSION_FIELDS = gql`
  fragment SessionFields on AuthPayload {
    accessToken
    refreshToken
    user {
      id
      email
      username
      displayName
    }
  }
`;

export const POST_FIELDS = gql`
  fragment PostFields on Post {
    id
    body
    visibility
    toxicityScore
    createdAt
    user {
      id
      username
      displayName
    }
  }
`;

export const MESSAGE_FIELDS = gql`
  fragment MessageFields on Message {
    id
    body
    toxicityScore
    readAt
    createdAt
    sender {
      id
      username
      displayName
    }
    recipient {
      id
      username
      displayName
    }
  }
`;

export const NOTIFICATION_FIELDS = gql`
  fragment NotificationFields on Notification {
    id
    kind
    payload
    readAt
    createdAt
  }
`;

export const REGISTER = gql`
  ${SESSION_FIELDS}
  mutation Register($email: String!, $username: String!, $displayName: String, $password: String!) {
    registerUser(email: $email, username: $username, displayName: $displayName, password: $password) {
      authPayload {
        ...SessionFields
      }
      errors
    }
  }
`;

export const LOGIN = gql`
  ${SESSION_FIELDS}
  mutation Login($email: String!, $password: String!) {
    loginUser(email: $email, password: $password) {
      authPayload {
        ...SessionFields
      }
      errors
    }
  }
`;

export const DASHBOARD_QUERY = gql`
  ${POST_FIELDS}
  ${MESSAGE_FIELDS}
  ${NOTIFICATION_FIELDS}
  query Dashboard {
    me {
      id
      email
      username
      displayName
    }
    users {
      id
      username
      displayName
    }
    feed {
      ...PostFields
    }
    messages {
      ...MessageFields
    }
    notifications {
      ...NotificationFields
    }
  }
`;

export const CREATE_POST = gql`
  ${POST_FIELDS}
  mutation CreatePost($body: String!, $visibility: String!) {
    createPost(body: $body, visibility: $visibility) {
      post {
        ...PostFields
      }
      errors
    }
  }
`;

export const FOLLOW_USER = gql`
  mutation FollowUser($followeeId: ID!) {
    followUser(followeeId: $followeeId) {
      follow {
        id
      }
      errors
    }
  }
`;

export const SEND_MESSAGE = gql`
  ${MESSAGE_FIELDS}
  mutation SendMessage($recipientId: ID!, $body: String!) {
    sendMessage(recipientId: $recipientId, body: $body) {
      message {
        ...MessageFields
      }
      errors
    }
  }
`;

export const SEARCH_POSTS = gql`
  ${POST_FIELDS}
  query SearchPosts($query: String!) {
    searchPosts(query: $query) {
      ...PostFields
    }
  }
`;

export const MARK_NOTIFICATION_READ = gql`
  ${NOTIFICATION_FIELDS}
  mutation MarkNotificationRead($id: ID!) {
    markNotificationRead(id: $id) {
      notification {
        ...NotificationFields
      }
    }
  }
`;
