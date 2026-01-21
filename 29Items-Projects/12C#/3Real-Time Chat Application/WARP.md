# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

TeamChat is a production-ready real-time team chat application built with ASP.NET Core 9.0, SignalR, MongoDB, Redis, Blazor WebAssembly, and ML.NET sentiment analysis. It follows Clean Architecture principles with clear separation between layers.

## Development Commands

### Building & Running

```powershell
# Build entire solution
dotnet build TeamChat.sln

# Build in Release mode
dotnet build TeamChat.sln --configuration Release

# Restore dependencies
dotnet restore TeamChat.sln

# Clean build artifacts
dotnet clean TeamChat.sln

# Run API server (from root)
dotnet run --project src/TeamChat.API/TeamChat.API.csproj

# Run Blazor client (from root)
dotnet run --project src/TeamChat.Client/TeamChat.Client.csproj
```

### Testing

```powershell
# Run all tests
dotnet test

# Run unit tests only (excludes Integration tests)
dotnet test --filter "Category!=Integration"

# Run with code coverage
dotnet test --collect:"XPlat Code Coverage"

# Run tests with detailed output
dotnet test --verbosity normal

# Run specific test project
dotnet test tests/TeamChat.Tests/TeamChat.Tests.csproj
```

### Docker

```powershell
# Start all services (API, Client, MongoDB, Redis, admin UIs)
docker-compose up -d

# Stop all services
docker-compose down

# View logs
docker-compose logs -f

# Rebuild and start
docker-compose up -d --build

# Access points after docker-compose up:
# - Web UI: http://localhost:5173
# - API: http://localhost:5000
# - Swagger: http://localhost:5000/swagger
# - MongoDB UI: http://localhost:8081
# - Redis UI: http://localhost:8082
```

## Architecture

### Clean Architecture Layers

The codebase follows Clean Architecture with four distinct layers:

**1. TeamChat.Core** (Domain Layer)
- Contains domain entities (User, Message, Channel, MessageThread, Reaction, FileAttachment)
- Defines interfaces for repositories and services (IUserRepository, IMessageRepository, IChannelRepository, IThreadRepository, IPresenceService, ISentimentAnalyzer, IFileStorageService)
- Contains DTOs for data transfer
- Enums for domain concepts (MessageType, Sentiment, UserStatus, etc.)
- **No external dependencies** - pure business logic

**2. TeamChat.Infrastructure** (Data/External Services Layer)
- Implements Core interfaces
- MongoDB repositories in `MongoDB/Repositories/` (UserRepository, MessageRepository, ChannelRepository, ThreadRepository)
- Redis services in `Redis/` (PresenceTracker, RedisPubSubService)
- ML.NET sentiment analysis in `ML/` (SentimentAnalysisService)
- File storage in `Storage/` (LocalFileStorageService)
- Configuration in `DependencyInjection.cs` registers all services

**3. TeamChat.API** (Presentation/Backend)
- REST Controllers in `Controllers/` for CRUD operations
- SignalR Hubs in `Hubs/` (ChatHub for messaging, PresenceHub for user status)
- Middleware in `Middleware/` for error handling
- JWT authentication service in `Services/` (JwtService)
- Extensions in `Extensions/` for service registration (SignalR with Redis backplane, CORS)
- Entry point: `Program.cs` configures middleware pipeline, JWT auth, SignalR, CORS

**4. TeamChat.Client** (Presentation/Frontend)
- Blazor WebAssembly SPA
- Pages in `Pages/` for routing
- Reusable components in `Components/`
- Services in `Services/` for API calls and SignalR connections
- Shared layouts in `Layout/`

### Key Architectural Decisions

**SignalR with Redis Backplane**: Enables horizontal scaling - multiple API instances can serve clients, Redis coordinates WebSocket messages across all servers.

**JWT Authentication for SignalR**: Tokens passed via query string (`access_token`) for WebSocket connections since WebSocket headers can't be customized. See `Program.cs` line 107-122.

**ML.NET Sentiment Analysis**: Runs in-process during message send to tag messages with sentiment (Positive, Negative, Neutral) without external API calls.

**MongoDB Document Structure**: Messages contain embedded reactions and file attachments. Threads are separate documents referencing parent messages.

**Presence Tracking**: Redis stores user status with TTL. PresenceHub updates status on connect/disconnect and periodic heartbeats.

## Configuration

### Required Environment Variables

The application uses hierarchical configuration with `__` as separator. Copy `.env.example` to `.env`:

```bash
# CRITICAL - JWT secret must be at least 32 characters
Jwt__Secret=your-secure-jwt-secret-key-at-least-32-characters

# Database connections
MongoDB__ConnectionString=mongodb://localhost:27017
MongoDB__DatabaseName=TeamChat
Redis__ConnectionString=localhost:6379

# CORS - add your frontend URLs
Cors__AllowedOrigins__0=http://localhost:5173
```

### Settings Files

- `appsettings.json` - Base configuration
- `appsettings.Development.json` - Development overrides
- Environment variables override appsettings (preferred for production)

## SignalR Hub Patterns

### ChatHub (`/hubs/chat`)

Key methods for real-time messaging:
- `SendMessage(channelId, content, threadId?)` - Send message to channel or thread
- `EditMessage(messageId, newContent)` - Edit existing message
- `DeleteMessage(messageId)` - Soft delete message
- `AddReaction(messageId, reactionType)` - Add emoji reaction
- `RemoveReaction(messageId, reactionType)` - Remove reaction
- `StartTyping(channelId)`, `StopTyping(channelId)` - Typing indicators

**Authentication**: Hub methods use `Context.User` from JWT token. All methods validate user authorization.

**Broadcasting**: Messages sent to specific channel groups. Use `Clients.Group(channelId)` pattern.

### PresenceHub (`/hubs/presence`)

- `UpdateStatus(status)` - Update online/away/offline status
- Tracks connection IDs in Redis with TTL
- Publishes status changes to all connected clients

## Testing Strategy

**Test Projects**: All tests in `tests/TeamChat.Tests/`

**Test Categories**:
- Unit tests in `Unit/` - Fast, no external dependencies (mocked repositories)
- Integration tests in `Integration/` - Use Testcontainers for real MongoDB/Redis instances
- Test fixtures in `Fixtures/` - Shared setup like WebApplicationFactory

**Running Integration Tests**: Requires Docker running locally (Testcontainers spins up containers automatically).

**CI Pipeline**: `.github/workflows/ci.yml` runs unit tests on push, integration tests are optional due to Docker requirement.

## Important Patterns & Conventions

### Dependency Injection

All service registration happens in:
- `TeamChat.Infrastructure/DependencyInjection.cs` for infrastructure services
- `TeamChat.API/Program.cs` and `Extensions/ServiceCollectionExtensions.cs` for API-specific services

Use `builder.Services.AddInfrastructure(configuration)` in Program.cs to register all infrastructure.

### MongoDB Collections

Collections are automatically created by MongoDbContext. Entity mapping happens in repository constructors using `BsonClassMap` (if needed, but typically uses default conventions).

### Redis Key Patterns

- Presence: `TeamChat_user:{userId}:status`
- Typing: `TeamChat_typing:{channelId}:{userId}`
- Pub/Sub channels: `TeamChat:presence:update`, `TeamChat:message:new`

### Error Handling

Global error handling middleware catches exceptions and returns consistent JSON error responses. See `Middleware/ErrorHandlingMiddleware.cs` (configured in Program.cs via `app.UseErrorHandling()`).

### JWT Token Format

Tokens include claims: `sub` (userId), `unique_name` (username), `email`. Access tokens expire after configured minutes (default 60). Refresh tokens not yet implemented.

## Common Development Workflows

### Adding a New Entity

1. Create entity class in `TeamChat.Core/Entities/`
2. Create repository interface in `TeamChat.Core/Interfaces/`
3. Implement repository in `TeamChat.Infrastructure/MongoDB/Repositories/`
4. Register in `TeamChat.Infrastructure/DependencyInjection.cs`
5. Create DTOs in `TeamChat.Core/DTOs/` for API contracts
6. Add controller in `TeamChat.API/Controllers/` or hub methods

### Adding a SignalR Hub Method

1. Add method to hub class in `TeamChat.API/Hubs/`
2. Mark with `[Authorize]` if authentication required
3. Use `Context.User` to get current user claims
4. Call repository methods for data access
5. Broadcast to clients using `Clients.Group()`, `Clients.All()`, or `Clients.Caller`

### Adding a New API Endpoint

1. Create/update controller in `TeamChat.API/Controllers/`
2. Add `[Authorize]` attribute if auth required
3. Inject required repositories via constructor
4. Return appropriate status codes and DTOs
5. Document in README.md API section if public-facing

## Health Checks & Monitoring

- Health check endpoint: `GET /health` returns "Healthy" when running
- No database connectivity checks in health endpoint currently (add if needed)

## Troubleshooting

**MongoDB Connection Issues**: Ensure MongoDB is running. Default expects `localhost:27017`. Check connection string in configuration.

**Redis Connection Issues**: Ensure Redis is running. Default expects `localhost:6379`. SignalR backplane is optional - app works without Redis but won't scale horizontally.

**JWT Token Errors**: Verify `Jwt:Secret` is set and matches between client/server. Ensure token isn't expired.

**SignalR Connection Failures**: Check CORS settings include client origin. Verify JWT token passed via `access_token` query parameter.

**Build Errors on Windows**: Use PowerShell (not cmd). Paths with spaces may need quotes.
