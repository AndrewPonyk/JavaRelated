# Real-Time Chat App

A production-oriented room chat application built as a Rust modular monolith. Axum and Tokio serve a framework-free responsive browser client, authenticated JSON APIs, and real-time WebSockets. PostgreSQL stores accounts, sessions, rooms, memberships, messages, idempotency keys, and immutable moderation audit events.

## What the app can do

1. Create a user account with a validated username and password.
2. Log in with an existing username and password.
3. Keep the user signed in with a secure, expiring browser session.
4. Show the currently signed-in user's profile and session identity.
5. Change the current user's username.
6. Change the current user's password and revoke all existing sessions.
7. Log out and immediately revoke the current session.
8. Delete the current user's account after password confirmation.
9. Browse public chat rooms without logging in.
10. Browse all public rooms plus joined private rooms after logging in.
11. Create a public or private chat room and become its owner.
12. View room details, member count, online count, and the current user's role.
13. Edit a room's name, description, and privacy settings as an owner or moderator.
14. Delete a room as its owner.
15. Join a public room.
16. Invite a user to a room by username as an owner or moderator.
17. View a room's member list with pagination.
18. Promote a member to moderator or demote a moderator as the room owner.
19. Leave a room or remove another member when the current role permits it.
20. Load durable message history in pages, including older messages.
21. Send a message and deliver it live to all connected room members.
22. Persist each message before broadcasting it so chat history survives restarts.
23. Retry a message safely without creating duplicates.
24. Edit a message as its sender or an authorized moderator.
25. Delete a message as its sender or an authorized moderator while retaining an audit-safe tombstone.
26. Show message edits and deletions to connected users in real time.
27. Show live typing indicators.
28. Show user join, leave, and online-count updates.
29. Reconnect dropped WebSocket connections and resynchronize missed history.
30. Let owners and moderators review an immutable room audit history.
31. Validate forms and display loading, empty, and error states in the browser.
32. Work on desktop and mobile through a responsive interface.

## Features

- Argon2id password hashing and revocable, expiring `HttpOnly` session cookies
- User profile, password, session, and account lifecycle
- Public and private rooms with owner, moderator, and member roles
- Durable cursor-paginated message history
- Idempotent message sends, editing, soft deletion, typing, and presence
- Bounded reconnect with jitter and history resynchronization
- Per-user/IP rate limits, connection caps, frame limits, and heartbeat cleanup
- Stable JSON error envelopes, request IDs, security headers, structured logs, and health probes
- Forward-only SQLx migrations and immutable room audit events
- Real PostgreSQL API/repository tests and two-client WebSocket integration tests

## How the application runs

This is a full-stack web application with three runtime components:

- **Browser client:** framework-free HTML, CSS, and JavaScript served from `frontend/`. It renders the responsive UI, validates forms, calls the JSON API, and maintains the live chat WebSocket connection. There is no separate Node.js frontend server or frontend build step.
- **Rust application server:** one Axum/Tokio process built from `backend/`. It serves the browser files, exposes the REST API under `/api`, handles authentication and business rules, provides health checks, and hosts room WebSockets at `/ws/{room_id}`.
- **PostgreSQL:** the authoritative persistent store for users, sessions, rooms, memberships, messages, idempotency records, and audit events. SQLx migrations run automatically when the Rust server starts.

The request flow is:

```text
Browser
  |-- GET /, /app.js, /styles.css --> Rust/Axum static file service
  |-- REST /api/* -----------------> Rust handlers/business logic --> PostgreSQL
  `-- WebSocket /ws/{room_id} -----> Rust room hub -------------> PostgreSQL
                                          |
                                          `--> live events to connected room members
```

Messages are written to PostgreSQL before the server broadcasts them. REST endpoints handle account, room, membership, moderation, and message-history operations; WebSockets handle live messages, edits, deletions, typing, presence, and connection health.

For local deployment, Docker Compose starts two services: `postgres` for the database and `app` for the complete Rust-backed web application. The browser connects to the single exposed application address, `http://localhost:8080`.

## Quick start

Requirements: Docker Desktop or another Docker Engine with Compose.

```powershell
Copy-Item .env.example .env
# Edit .env and replace the local PostgreSQL password placeholder.
docker compose up --build
```

Open `http://localhost:8080`, create an account, and join the seeded `General` room or create your own. Compose waits for PostgreSQL, applies every migration, starts the app, and exposes readiness checks. Stop without deleting data:

```powershell
docker compose down
```

To intentionally erase the local database volume, use `docker compose down --volumes`.

## Run from source

Requirements: Rust 1.88, PostgreSQL 17, and `cargo-llvm-cov` for the coverage gate.

```powershell
Copy-Item .env.example .env
docker compose up -d postgres
cargo run --bin chat-server
```

The server reads `.env`, validates configuration, applies `migrations/`, serves the frontend, and listens on `http://localhost:8080` by default. Run migrations without serving traffic with:

```powershell
cargo run --bin chat-server -- --migrate-only
```

## Tests and quality gates

The project’s single validation command starts the test database, checks formatting and strict Clippy, runs unit and real-database integration tests, enforces at least 80% line coverage, builds release artifacts, and builds the production container. It reads local test credentials from the ignored `.env` file:

```powershell
./scripts/check.ps1
```

The measured suite currently covers more than 85% of Rust lines. To run only the tests:

```powershell
$env:TEST_DATABASE_URL = "postgres://chat:<local-password>@localhost:5432/postgres"
cargo test --workspace --all-features --locked
```

Integration tests create isolated temporary databases and remove them after each successful run.

## API overview

All mutation bodies are JSON. Browser authentication uses the `chat_session` cookie; API clients should retain `Set-Cookie` from register/login. State-changing browser requests are same-origin protected.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Register and start a session |
| `POST` | `/api/auth/login` | Authenticate and start a session |
| `POST` | `/api/auth/logout` | Revoke the current session |
| `GET` | `/api/auth/me` | Get the current session identity |
| `GET/PUT/DELETE` | `/api/users/me` | Read, rename, or delete the current account |
| `POST` | `/api/users/me/password` | Change password and revoke all sessions |
| `GET/POST` | `/api/rooms` | List visible rooms or create one |
| `GET/PUT/DELETE` | `/api/rooms/{room_id}` | Read, update, or delete a room |
| `POST` | `/api/rooms/{room_id}/join` | Join a public room |
| `GET/POST` | `/api/rooms/{room_id}/members` | List or invite members |
| `PUT/DELETE` | `/api/rooms/{room_id}/members/{user_id}` | Change a role or remove a member |
| `GET/POST` | `/api/rooms/{room_id}/messages` | Paginate history or create a message |
| `GET/PUT/DELETE` | `/api/rooms/{room_id}/messages/{message_id}` | Read, edit, or soft-delete a message |
| `GET` | `/api/rooms/{room_id}/audit` | Read moderator audit history |
| `GET` | `/ws/{room_id}` | Upgrade an authenticated member to WebSocket |
| `GET` | `/health/live` | Process liveness |
| `GET` | `/health/ready` | PostgreSQL readiness |

See [API documentation](docs/API.md) for bodies, authorization, protocol events, pagination, and errors.

## Configuration

`.env.example` is the complete local inventory. Important production values are:

- `DATABASE_URL`: required PostgreSQL connection URL
- `ALLOWED_ORIGINS`: comma-separated exact HTTPS origins
- `SECURE_COOKIES=true`: required in staging/production
- `SESSION_TTL_HOURS`: session lifetime, 1–720 hours
- `MAX_LIST_PAGE_SIZE` and `MAX_HISTORY_PAGE_SIZE`: bounded API page sizes
- `PASSWORD_HASH_CONCURRENCY`: upper bound for concurrent Argon2 work
- `MAX_MESSAGE_BYTES`: server-side UTF-8 message limit, at most 4096
- `MESSAGES_PER_MINUTE` and `API_REQUESTS_PER_MINUTE`: fixed-window safety limits
- `MAX_CONNECTIONS_PER_ROOM` and `CHAT_CHANNEL_CAPACITY`: bounded live resources
- `HEARTBEAT_INTERVAL_SECONDS` and `HEARTBEAT_TIMEOUT_SECONDS`: stale socket cleanup

The server rejects malformed, zero, unbounded, or insecure production values at startup. Secrets belong in `.env` locally or the deployment platform’s secret store, never in checked-in TOML files.

## Deployment

The multi-stage image runs as an unprivileged user and contains only the server, migrations, CA certificates, health-check client, and static frontend. Fly configurations run migrations as a release command and gate traffic on `/health/ready`.

1. Create separate Fly applications and PostgreSQL databases for staging and production.
2. Set `DATABASE_URL` and `ALLOWED_ORIGINS` with `fly secrets set`; use an HTTPS origin.
3. Set the protected GitHub environment variable `FLY_APP_NAME` and secret `FLY_API_TOKEN` for each environment.
4. Push `main`; staging deploys only after the CI workflow succeeds. Dispatch the deployment workflow for an environment-approved production deployment.

Manual deployment uses an explicit app name:

```powershell
fly deploy --config fly.staging.toml --app <staging-app-name>
fly deploy --config fly.toml --app <production-app-name>
```

Keep one application machine until the documented external event-bus and distributed-presence growth step is deliberately introduced; durable history remains safe in PostgreSQL.

## Architecture and operations

- [Project plan and implementation status](docs/PROJECT-PLAN.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Technical notes](docs/TECH-NOTES.md)
- [API contract](docs/API.md)

Message content, credentials, cookies, and tokens are excluded from logs. PostgreSQL is authoritative: a message is persisted before it is broadcast. Room deletion cascades memberships and messages, while individual message deletion retains a content-free tombstone for ordering and moderation history.

## Troubleshooting

- **Compose reports a missing variable:** copy `.env.example` to `.env` and replace every `replace-with-a-local-password` occurrence with the same URL-safe local password.
- **Port 5432 or 8080 is already in use:** stop the conflicting local process/container or change the host-side port mapping in `docker-compose.yml`.
- **Readiness returns 503:** check `docker compose logs postgres app`; the app intentionally stays unready until PostgreSQL accepts `SELECT 1` and migrations finish.
- **Integration tests say `TEST_DATABASE_URL` is missing:** run `./scripts/check.ps1` or export the test administrator URL manually. The database user must be allowed to create temporary databases.
- **A browser mutation returns 403:** browser and API mutation requests must send an exact origin listed in `ALLOWED_ORIGINS`; production origins must use HTTPS.
- **Login cookies do not persist in production:** set `SECURE_COOKIES=true`, terminate traffic with HTTPS, and ensure `ALLOWED_ORIGINS` exactly matches the public origin.
- **Reset local data:** `docker compose down --volumes` permanently removes the development database; plain `docker compose down` preserves it.
