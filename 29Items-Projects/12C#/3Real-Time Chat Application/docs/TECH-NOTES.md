# TeamChat - Technical Notes

## 1. CI/CD Pipeline Design

### GitHub Actions Workflow

```yaml
# Pipeline stages
┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐
│  Lint   │ → │  Test   │ → │  Build  │ → │  Push   │ → │ Deploy  │
└─────────┘   └─────────┘   └─────────┘   └─────────┘   └─────────┘
```

### CI Pipeline (ci.yml)

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  build-and-test:
    runs-on: ubuntu-latest

    services:
      mongodb:
        image: mongo:7
        ports:
          - 27017:27017
      redis:
        image: redis:7
        ports:
          - 6379:6379

    steps:
      - uses: actions/checkout@v4

      - name: Setup .NET
        uses: actions/setup-dotnet@v4
        with:
          dotnet-version: '9.0.x'

      - name: Restore
        run: dotnet restore

      - name: Build
        run: dotnet build --no-restore

      - name: Test
        run: dotnet test --no-build --verbosity normal --collect:"XPlat Code Coverage"

      - name: Upload coverage
        uses: codecov/codecov-action@v3
```

### CD Pipeline (deploy.yml)

```yaml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: production

    steps:
      - uses: actions/checkout@v4

      - name: Build Docker image
        run: docker build -t teamchat-api ./src/TeamChat.API

      - name: Push to ACR
        run: |
          az acr login --name ${{ secrets.ACR_NAME }}
          docker tag teamchat-api ${{ secrets.ACR_NAME }}.azurecr.io/teamchat-api:${{ github.sha }}
          docker push ${{ secrets.ACR_NAME }}.azurecr.io/teamchat-api:${{ github.sha }}

      - name: Deploy to Azure Container Apps
        run: |
          az containerapp update \
            --name teamchat-api \
            --resource-group teamchat-rg \
            --image ${{ secrets.ACR_NAME }}.azurecr.io/teamchat-api:${{ github.sha }}
```

---

## 2. Testing Strategy

### 2.1 Unit Testing

**Framework:** xUnit + Moq + FluentAssertions

**Coverage Target:** 80% for Core and Infrastructure

```csharp
public class MessageRepositoryTests
{
    private readonly Mock<IMongoCollection<Message>> _mockCollection;
    private readonly MessageRepository _repository;

    [Fact]
    public async Task GetByChannelId_ShouldReturnMessages()
    {
        // Arrange
        var channelId = "channel-123";
        var messages = new List<Message> { /* ... */ };
        _mockCollection.Setup(x => x.FindAsync(...)).ReturnsAsync(messages);

        // Act
        var result = await _repository.GetByChannelIdAsync(channelId);

        // Assert
        result.Should().HaveCount(messages.Count);
    }
}
```

### 2.2 Integration Testing

**Framework:** Microsoft.AspNetCore.Mvc.Testing + Testcontainers

```csharp
public class ChatHubTests : IClassFixture<WebApplicationFactory<Program>>
{
    [Fact]
    public async Task SendMessage_ShouldBroadcastToChannel()
    {
        // Arrange
        var connection = new HubConnectionBuilder()
            .WithUrl("http://localhost/hubs/chat")
            .Build();

        var receivedMessages = new List<MessageDto>();
        connection.On<MessageDto>("ReceiveMessage", msg => receivedMessages.Add(msg));

        await connection.StartAsync();
        await connection.InvokeAsync("JoinChannel", "channel-123");

        // Act
        await connection.InvokeAsync("SendMessage", "channel-123", "Hello!");

        // Assert
        await Task.Delay(100); // Allow message propagation
        receivedMessages.Should().ContainSingle(m => m.Content == "Hello!");
    }
}
```

### 2.3 End-to-End Testing

**Tools:** Playwright for Blazor WASM testing

```csharp
[Test]
public async Task UserCanSendAndReceiveMessages()
{
    await Page.GotoAsync("http://localhost:5000");
    await Page.FillAsync("#message-input", "Hello, World!");
    await Page.ClickAsync("#send-button");

    var message = await Page.WaitForSelectorAsync(".message:has-text('Hello, World!')");
    Assert.NotNull(message);
}
```

---

## 3. Deployment Strategy

### 3.1 Container Architecture

```dockerfile
# src/TeamChat.API/Dockerfile
FROM mcr.microsoft.com/dotnet/aspnet:9.0 AS base
WORKDIR /app
EXPOSE 80
EXPOSE 443

FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
WORKDIR /src
COPY ["src/TeamChat.API/TeamChat.API.csproj", "TeamChat.API/"]
COPY ["src/TeamChat.Core/TeamChat.Core.csproj", "TeamChat.Core/"]
COPY ["src/TeamChat.Infrastructure/TeamChat.Infrastructure.csproj", "TeamChat.Infrastructure/"]
RUN dotnet restore "TeamChat.API/TeamChat.API.csproj"
COPY src/ .
RUN dotnet build "TeamChat.API/TeamChat.API.csproj" -c Release -o /app/build

FROM build AS publish
RUN dotnet publish "TeamChat.API/TeamChat.API.csproj" -c Release -o /app/publish

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "TeamChat.API.dll"]
```

### 3.2 Azure Deployment Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Azure                                 │
│                                                             │
│  ┌─────────────────┐     ┌─────────────────────────────┐   │
│  │  Azure Front    │     │    Azure Container Apps     │   │
│  │     Door        │────▶│    (TeamChat.API)           │   │
│  └─────────────────┘     └──────────────┬──────────────┘   │
│                                         │                   │
│         ┌───────────────────────────────┼───────────────┐  │
│         │                               │               │  │
│         ▼                               ▼               ▼  │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    │
│  │   Azure     │    │Azure Cache  │    │  Azure      │    │
│  │  SignalR    │    │ for Redis   │    │ Blob Storage│    │
│  │  Service    │    └─────────────┘    └─────────────┘    │
│  └─────────────┘                                          │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                MongoDB Atlas                         │   │
│  │            (or Azure Cosmos DB)                      │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 Blazor Client Deployment

```bash
# Build for production
dotnet publish src/TeamChat.Client -c Release -o ./publish/client

# Deploy to Azure Static Web Apps or CDN
az storage blob upload-batch \
  --account-name teamchatstorage \
  --destination '$web' \
  --source ./publish/client/wwwroot
```

---

## 4. Environment Management

### 4.1 Configuration Hierarchy

```
1. appsettings.json              (defaults)
2. appsettings.{Environment}.json (environment-specific)
3. Environment variables          (overrides)
4. User secrets                   (dev only)
5. Azure Key Vault                (production secrets)
```

### 4.2 Environment Variables

```bash
# .env.example

# Application
ASPNETCORE_ENVIRONMENT=Development
ASPNETCORE_URLS=http://+:5000

# MongoDB
MONGODB_CONNECTION_STRING=mongodb://localhost:27017
MONGODB_DATABASE_NAME=TeamChat

# Redis
REDIS_CONNECTION_STRING=localhost:6379
REDIS_INSTANCE_NAME=TeamChat_

# Azure SignalR (production only)
AZURE_SIGNALR_CONNECTION_STRING=

# Azure Blob Storage
AZURE_STORAGE_CONNECTION_STRING=
AZURE_STORAGE_CONTAINER_NAME=uploads

# JWT
JWT_SECRET=your-secret-key-here-min-32-chars
JWT_ISSUER=TeamChat
JWT_AUDIENCE=TeamChat

# ML.NET
ML_MODEL_PATH=./ML/Models/sentiment_model.zip
```

### 4.3 appsettings Structure

```json
{
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Microsoft.AspNetCore": "Warning",
      "Microsoft.AspNetCore.SignalR": "Debug"
    }
  },
  "MongoDB": {
    "ConnectionString": "mongodb://localhost:27017",
    "DatabaseName": "TeamChat"
  },
  "Redis": {
    "ConnectionString": "localhost:6379",
    "InstanceName": "TeamChat_"
  },
  "Jwt": {
    "Secret": "",
    "Issuer": "TeamChat",
    "Audience": "TeamChat",
    "ExpiryMinutes": 60
  },
  "FileStorage": {
    "Provider": "Local",
    "LocalPath": "./uploads",
    "MaxFileSizeMB": 10,
    "AllowedExtensions": [".jpg", ".png", ".gif", ".pdf", ".doc", ".docx"]
  }
}
```

---

## 5. Version Control Workflow

### GitHub Flow (Recommended)

```
main ─────●─────────●─────────●─────────●───────▶
          │         ▲         │         ▲
          │         │         │         │
          ▼         │         ▼         │
feature/  ●────●────┘         │         │
add-reactions                 │         │
                              ▼         │
feature/                      ●────●────┘
file-sharing
```

### Branch Naming Convention

| Type | Pattern | Example |
|------|---------|---------|
| Feature | `feature/<description>` | `feature/add-reactions` |
| Bug fix | `fix/<description>` | `fix/message-ordering` |
| Hotfix | `hotfix/<description>` | `hotfix/connection-leak` |
| Release | `release/<version>` | `release/1.2.0` |

### Commit Message Convention

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

Examples:
```
feat(chat): add message reactions
fix(presence): resolve reconnection issue
docs(readme): update deployment instructions
```

### Pull Request Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual testing completed

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Documentation updated
```

---

## 6. Common Pitfalls

### 6.1 SignalR Pitfalls

| Pitfall | Solution |
|---------|----------|
| **Connection limit on single server** | Use Redis backplane or Azure SignalR Service |
| **Sticky sessions required with load balancer** | Configure ARR affinity or use Azure SignalR |
| **Memory leaks from undisposed connections** | Implement proper OnDisconnectedAsync handling |
| **Message ordering issues** | Use sequential message IDs, client-side ordering |
| **Reconnection storms** | Implement exponential backoff in client |

```csharp
// Client-side reconnection with backoff
var connection = new HubConnectionBuilder()
    .WithUrl("/hubs/chat")
    .WithAutomaticReconnect(new[] {
        TimeSpan.Zero,
        TimeSpan.FromSeconds(2),
        TimeSpan.FromSeconds(5),
        TimeSpan.FromSeconds(10),
        TimeSpan.FromSeconds(30)
    })
    .Build();
```

### 6.2 MongoDB Pitfalls

| Pitfall | Solution |
|---------|----------|
| **No indexes on query fields** | Create indexes on channelId, createdAt, userId |
| **Unbounded array growth** | Use bucket pattern for reactions |
| **Connection pool exhaustion** | Configure MaxConnectionPoolSize |
| **Large documents** | Keep messages small, store files separately |

```csharp
// Create indexes on startup
public async Task CreateIndexes()
{
    var indexKeys = Builders<Message>.IndexKeys
        .Ascending(m => m.ChannelId)
        .Descending(m => m.CreatedAt);

    await _messages.Indexes.CreateOneAsync(
        new CreateIndexModel<Message>(indexKeys));
}
```

### 6.3 Redis Pitfalls

| Pitfall | Solution |
|---------|----------|
| **Connection multiplexer not shared** | Use singleton ConnectionMultiplexer |
| **Key naming collisions** | Use consistent prefix (e.g., `teamchat:`) |
| **Memory bloat from presence data** | Set TTL on presence keys |
| **Pub/sub message loss** | Implement acknowledgment for critical messages |

```csharp
// Singleton Redis connection
services.AddSingleton<IConnectionMultiplexer>(sp =>
    ConnectionMultiplexer.Connect(configuration["Redis:ConnectionString"]));
```

### 6.4 Blazor WASM Pitfalls

| Pitfall | Solution |
|---------|----------|
| **Large download size** | Enable AOT compilation, trim unused assemblies |
| **SignalR reconnection UI** | Show connection status indicator |
| **State lost on refresh** | Use localStorage/sessionStorage |
| **Thread blocking** | Use async/await everywhere |

### 6.5 ML.NET Pitfalls

| Pitfall | Solution |
|---------|----------|
| **Cold start latency** | Pre-load model on app startup |
| **Thread safety** | Use PredictionEnginePool |
| **Model file not found in container** | Embed as resource or use consistent path |

```csharp
// Use PredictionEnginePool for thread safety
services.AddPredictionEnginePool<SentimentInput, SentimentPrediction>()
    .FromFile(modelPath);
```

---

## 7. Performance Checklist

- [ ] MongoDB indexes created for all query patterns
- [ ] Redis connection multiplexer is singleton
- [ ] SignalR using Redis backplane or Azure SignalR
- [ ] Message pagination implemented (50 per page)
- [ ] File uploads use streaming (not loading into memory)
- [ ] Blazor WASM lazy loads non-critical components
- [ ] Health checks configured for all dependencies
- [ ] Response compression enabled
- [ ] Static files cached with appropriate headers
