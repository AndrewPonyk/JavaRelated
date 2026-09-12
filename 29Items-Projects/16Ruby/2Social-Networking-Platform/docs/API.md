# API Documentation

## Authentication

REST endpoints accept `Authorization: Bearer <access_token>`. GraphQL uses the same header.
Collection endpoints accept an optional `limit` query parameter. REST limits are clamped to safe maximums between 50 and 100 depending on the endpoint.

### REST Auth

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `DELETE /api/auth/logout`
- `GET /api/auth/me`

Register body:

```json
{
  "user": {
    "email": "demo@example.com",
    "username": "demo_user",
    "display_name": "Demo User",
    "password": "password123",
    "password_confirmation": "password123"
  }
}
```

## REST Resources

All resources support JSON responses and validation errors in this shape:

```json
{ "errors": ["Body can't be blank"] }
```

### Users

- `GET /api/users`
- `GET /api/users/:id`
- `POST /api/users`
- `PATCH /api/users/:id`
- `DELETE /api/users/:id`

### Posts

- `GET /api/posts`
- `GET /api/posts/:id`
- `POST /api/posts`
- `PATCH /api/posts/:id`
- `DELETE /api/posts/:id`

### Follows

- `GET /api/follows`
- `GET /api/follows/:id`
- `POST /api/follows`
- `PATCH /api/follows/:id`
- `DELETE /api/follows/:id`

### Messages

- `GET /api/messages`
- `GET /api/messages/:id`
- `POST /api/messages`
- `PATCH /api/messages/:id`
- `DELETE /api/messages/:id`

### Notifications

- `GET /api/notifications`
- `GET /api/notifications/:id`
- `POST /api/notifications`
- `PATCH /api/notifications/:id`
- `DELETE /api/notifications/:id`

### Toxicity Results

- `GET /api/toxicity_results`
- `GET /api/toxicity_results/:id`
- `POST /api/toxicity_results`
- `PATCH /api/toxicity_results/:id`
- `DELETE /api/toxicity_results/:id`

### Search

- `GET /api/search?q=<query>`

Blank search queries return `400 Bad Request`.

## GraphQL

Endpoint: `POST /graphql`

Example dashboard query:

```graphql
query Dashboard {
  me {
    id
    username
    displayName
  }
  feed {
    id
    body
    visibility
    toxicityScore
    user {
      username
    }
  }
  messages {
    id
    body
    sender {
      username
    }
    recipient {
      username
    }
  }
  notifications {
    id
    kind
    readAt
  }
}
```

Main mutations:

- `registerUser(email:, username:, displayName:, password:)`
- `loginUser(email:, password:)`
- `createPost(body:, visibility:)`
- `updatePost(id:, body:, visibility:)`
- `deletePost(id:)`
- `followUser(followeeId:)`
- `unfollowUser(followeeId:)`
- `sendMessage(recipientId:, body:)`
- `markNotificationRead(id:)`

Main queries:

- `users(limit:)`
- `feed(limit:)`
- `posts(limit:)`
- `messages(limit:)`
- `notifications(limit:)`
- `searchPosts(query:, limit:)`

GraphQL list limits are clamped server-side and blank `searchPosts` terms return a GraphQL execution error.
