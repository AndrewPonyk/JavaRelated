# TeamChat - Real-Time Chat Application

A production-ready real-time team chat application built with ASP.NET Core 9.0, SignalR, MongoDB, Redis, Blazor WebAssembly, and ML.NET sentiment analysis.

![TeamChat Demo - Real-time messaging with typing indicators](./Chat-Bob-Alica.jpg)

## Features

- Real-time messaging with SignalR WebSockets
- Channel-based conversations with threading
- Message reactions with real-time updates
- AI-powered sentiment analysis (ML.NET)
- User presence tracking (online/away/offline)
- File attachments and sharing
- JWT-based authentication
- Redis backplane for horizontal scaling
- Docker support for easy deployment

## Quick Start

### Option 1: Docker Compose (Recommended)

```bash
# Clone the repository
git clone <repository-url>
cd TeamChat

# Start all services
docker-compose up -d

# Access the application
# - Web UI: http://localhost:5173
# - API: http://localhost:5000
# - Swagger: http://localhost:5000/swagger
```

### Option 2: Local Development

**Prerequisites:**
- .NET 9.0 SDK
- MongoDB (running on localhost:27017)
- Redis (running on localhost:6379)

```bash
# Build the solution
dotnet build

# Start the API (Terminal 1)
cd src/TeamChat.API
dotnet run

# Start the Client (Terminal 2)
cd src/TeamChat.Client
dotnet run

# Access: https://localhost:5001 (API) or http://localhost:5173 (Client)
```

### Option 3: Hybrid Debugging (Recommended for Development)

Run infrastructure (MongoDB, Redis) in Docker while debugging .NET projects in Visual Studio with full breakpoint support.

**Step 1: Start infrastructure services**
```bash
docker compose up mongodb redis -d
```

**Step 2: Open solution in Visual Studio**
1. Open `TeamChat.sln`
2. Right-click Solution → **Set Startup Projects**
3. Select **Multiple startup projects**
4. Set both **TeamChat.API** and **TeamChat.Client** to **Start**
5. Press **F5** to debug

**Ports:**
| Service | URL |
|---------|-----|
| Client | https://localhost:7278 |
| API | https://localhost:7076 |
| MongoDB | localhost:27017 |
| Redis | localhost:6379 |

**Debug features:**
- Set breakpoints in API controllers and SignalR hubs
- Step through message handling in `ChatHub.cs`
- Inspect real-time events (typing indicators, presence)
- Hot reload for Blazor components

**Key files for breakpoints:**
- `src/TeamChat.API/Hubs/ChatHub.cs:90` - Message sending
- `src/TeamChat.API/Hubs/ChatHub.cs:238` - Typing indicators
- `src/TeamChat.API/Controllers/AuthController.cs` - Authentication

## Configuration

### Environment Variables

Copy `.env.example` to `.env` and configure:

```bash
# Required - Generate a secure secret (min 32 chars)
Jwt__Secret=your-secure-jwt-secret-key-at-least-32-characters

# Database connections
MongoDB__ConnectionString=mongodb://localhost:27017
Redis__ConnectionString=localhost:6379

# CORS (add your frontend URLs)
Cors__AllowedOrigins__0=http://localhost:5173
```

### appsettings.json

For local development, settings are in `src/TeamChat.API/appsettings.Development.json`.
For production, use environment variables.

## API Documentation

### Authentication

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/auth/register` | POST | Register new user |
| `/api/auth/login` | POST | Login and get JWT token |
| `/api/auth/me` | GET | Get current user profile |
| `/api/auth/refresh` | POST | Refresh JWT token |

**Register Example:**
```bash
curl -X POST http://localhost:5000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"john","email":"john@example.com","password":"password123"}'
```

**Login Example:**
```bash
curl -X POST http://localhost:5000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"password123"}'
```

### Channels

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/channels` | GET | List all public channels |
| `/api/channels/{id}` | GET | Get channel details |
| `/api/channels` | POST | Create new channel |
| `/api/channels/{id}/join` | POST | Join a channel |
| `/api/channels/{id}/leave` | POST | Leave a channel |

### Messages

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/messages/channel/{channelId}` | GET | Get channel messages (paginated) |
| `/api/messages/thread/{threadId}` | GET | Get thread messages |
| `/api/messages/{id}` | GET | Get single message |

### Users

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/users/{id}` | GET | Get user by ID |
| `/api/users/username/{username}` | GET | Get user by username |
| `/api/users` | POST | Create user |

### SignalR Hubs

**Chat Hub** (`/hubs/chat`):
- `JoinChannel(channelId)` - Join a channel
- `LeaveChannel(channelId)` - Leave a channel
- `SendMessage(channelId, content, threadId?)` - Send message
- `EditMessage(messageId, newContent)` - Edit message
- `DeleteMessage(messageId)` - Delete message
- `AddReaction(messageId, reactionType)` - Add reaction
- `RemoveReaction(messageId, reactionType)` - Remove reaction
- `StartTyping(channelId)` - Start typing indicator
- `StopTyping(channelId)` - Stop typing indicator

**Presence Hub** (`/hubs/presence`):
- `UpdateStatus(status)` - Update user status

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Blazor WebAssembly Client                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  Pages      │  │ Components  │  │     Services        │  │
│  │  Login.razor│  │ MessageList │  │ AuthService         │  │
│  │  Chat.razor │  │ MessageInput│  │ ChatHubService      │  │
│  │  Index.razor│  │ NavMenu     │  │ ApiService          │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              │ HTTP/REST     │ WebSocket     │
              ▼               ▼               │
┌─────────────────────────────────────────────────────────────┐
│                    ASP.NET Core API                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ Controllers │  │  SignalR    │  │    Middleware       │  │
│  │ Auth        │  │  ChatHub    │  │ JWT Auth            │  │
│  │ Channels    │  │  Presence   │  │ Error Handling      │  │
│  │ Messages    │  │  Hub        │  │ Security Headers    │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   MongoDB   │    │    Redis    │    │   ML.NET    │
│  Users      │    │  PubSub     │    │  Sentiment  │
│  Channels   │    │  Presence   │    │  Analysis   │
│  Messages   │    │  Cache      │    │             │
└─────────────┘    └─────────────┘    └─────────────┘
```

## Running Tests

```bash
# Run all tests
dotnet test

# Run unit tests only
dotnet test --filter "FullyQualifiedName~Unit"

# Run integration tests (requires Docker)
dotnet test --filter "FullyQualifiedName~Integration"

# Run with coverage
dotnet test --collect:"XPlat Code Coverage"
```

## Troubleshooting

### Common Issues

**MongoDB Connection Failed**
- Ensure MongoDB is running: `mongod --dbpath /data/db`
- Check connection string in config matches your MongoDB instance

**Redis Connection Failed**
- Ensure Redis is running: `redis-server`
- Verify connection string: `localhost:6379`

**JWT Token Invalid**
- Ensure `Jwt:Secret` is set (min 32 characters)
- Check token hasn't expired (default: 60 minutes)
- Verify the token is included in Authorization header: `Bearer <token>`

**SignalR Connection Issues**
- Check CORS settings include your client URL
- For WebSocket issues, try long-polling fallback
- Ensure the Hub URL is correct: `/hubs/chat` or `/hubs/presence`

**Build Errors**
```bash
# Clean and rebuild
dotnet clean
dotnet restore
dotnet build
```

### Health Check

```bash
curl http://localhost:5000/health
# Response: Healthy
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Frontend | Blazor WebAssembly |
| Backend | ASP.NET Core 9.0 |
| Real-time | SignalR |
| Database | MongoDB 7 |
| Cache/PubSub | Redis 7 |
| ML | ML.NET |
| Auth | JWT Bearer |
| Testing | xUnit, FluentAssertions, Testcontainers |
| Container | Docker, docker-compose |

## License

MIT License
