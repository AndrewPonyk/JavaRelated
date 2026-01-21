# TeamChat - Project Plan

## Overview

TeamChat is a real-time team communication platform built with modern .NET technologies. It provides channels, threads, file sharing, reactions, and AI-powered sentiment analysis.

---

## 1. Project File Structure

```
TeamChat/
├── .github/
│   └── workflows/
│       ├── ci.yml                    # Continuous Integration
│       └── deploy.yml                # Deployment to Azure
│
├── docs/
│   ├── PROJECT-PLAN.md               # This file
│   ├── ARCHITECTURE.md               # System architecture
│   └── TECH-NOTES.md                 # Technical notes
│
├── src/
│   ├── TeamChat.Core/                # Domain layer (no dependencies)
│   │   ├── Entities/                 # Domain entities
│   │   │   ├── User.cs
│   │   │   ├── Message.cs
│   │   │   ├── Channel.cs
│   │   │   ├── Thread.cs
│   │   │   ├── Reaction.cs
│   │   │   └── FileAttachment.cs
│   │   ├── DTOs/                     # Data Transfer Objects
│   │   │   ├── MessageDto.cs
│   │   │   ├── ChannelDto.cs
│   │   │   ├── UserDto.cs
│   │   │   ├── ThreadDto.cs
│   │   │   └── SentimentResultDto.cs
│   │   ├── Interfaces/               # Contracts
│   │   │   ├── IMessageRepository.cs
│   │   │   ├── IChannelRepository.cs
│   │   │   ├── IUserRepository.cs
│   │   │   ├── IThreadRepository.cs
│   │   │   ├── IPresenceService.cs
│   │   │   ├── ISentimentAnalyzer.cs
│   │   │   └── IFileStorageService.cs
│   │   └── Enums/                    # Enumerations
│   │       ├── MessageType.cs
│   │       ├── ReactionType.cs
│   │       ├── Sentiment.cs
│   │       ├── ChannelType.cs
│   │       └── UserStatus.cs
│   │
│   ├── TeamChat.Infrastructure/      # External services implementation
│   │   ├── MongoDB/
│   │   │   ├── MongoDbContext.cs
│   │   │   ├── MongoDbSettings.cs
│   │   │   └── Repositories/
│   │   │       ├── MessageRepository.cs
│   │   │       ├── ChannelRepository.cs
│   │   │       ├── UserRepository.cs
│   │   │       └── ThreadRepository.cs
│   │   ├── Redis/
│   │   │   ├── RedisConnectionManager.cs
│   │   │   ├── PresenceTracker.cs
│   │   │   └── RedisPubSubService.cs
│   │   ├── Storage/
│   │   │   └── LocalFileStorageService.cs
│   │   ├── ML/
│   │   │   ├── SentimentAnalysisService.cs
│   │   │   ├── SentimentInput.cs
│   │   │   ├── SentimentPrediction.cs
│   │   │   └── Models/
│   │   │       └── (sentiment_model.zip)
│   │   └── DependencyInjection.cs
│   │
│   ├── TeamChat.API/                 # ASP.NET Core Web API + SignalR
│   │   ├── Hubs/
│   │   │   ├── ChatHub.cs
│   │   │   └── PresenceHub.cs
│   │   ├── Controllers/
│   │   │   ├── ChannelsController.cs
│   │   │   ├── MessagesController.cs
│   │   │   ├── UsersController.cs
│   │   │   └── FilesController.cs
│   │   ├── Extensions/
│   │   │   └── ServiceCollectionExtensions.cs
│   │   ├── Middleware/
│   │   │   └── ErrorHandlingMiddleware.cs
│   │   ├── Program.cs
│   │   ├── appsettings.json
│   │   └── appsettings.Development.json
│   │
│   └── TeamChat.Client/              # Blazor WebAssembly
│       ├── Pages/
│       │   ├── Index.razor
│       │   ├── Chat.razor
│       │   ├── Channel.razor
│       │   └── Settings.razor
│       ├── Components/
│       │   ├── MessageList.razor
│       │   ├── MessageInput.razor
│       │   ├── ChannelSidebar.razor
│       │   ├── UserPresence.razor
│       │   ├── ReactionPicker.razor
│       │   └── FileUpload.razor
│       ├── Services/
│       │   ├── ChatHubService.cs
│       │   └── ApiService.cs
│       ├── Shared/
│       │   ├── MainLayout.razor
│       │   └── NavMenu.razor
│       ├── wwwroot/
│       │   └── css/
│       │       └── app.css
│       └── Program.cs
│
├── tests/
│   └── TeamChat.Tests/
│       ├── Unit/
│       │   ├── MessageRepositoryTests.cs
│       │   ├── SentimentAnalysisTests.cs
│       │   └── PresenceTrackerTests.cs
│       ├── Integration/
│       │   ├── ChatHubTests.cs
│       │   └── ApiEndpointTests.cs
│       └── Fixtures/
│           └── TestFixture.cs
│
├── tools/
│   └── TeamChat.ML.Trainer/          # ML model training (separate)
│       ├── Program.cs
│       └── Data/
│           └── sentiment_data.csv
│
├── docker-compose.yml                # Local development
├── .env.example                      # Environment template
├── .gitignore
├── TeamChat.sln
└── README.md
```

---

## 2. Implementation TODO List

### Phase 1: Foundation (High Priority)

- [x] Create solution and project structure
- [x] Add project references
- [x] Add NuGet packages
- [ ] **Core Layer**
  - [ ] Define all entities (User, Message, Channel, Thread, Reaction, FileAttachment)
  - [ ] Create DTOs for API contracts
  - [ ] Define repository interfaces
  - [ ] Define service interfaces
  - [ ] Create enums
- [ ] **Infrastructure Layer**
  - [ ] Implement MongoDB context and settings
  - [ ] Implement MongoDB repositories
  - [ ] Implement Redis connection manager
  - [ ] Implement presence tracker with Redis
- [ ] **API Layer**
  - [ ] Configure Program.cs with SignalR + Redis backplane
  - [ ] Implement ChatHub (basic messaging)
  - [ ] Implement PresenceHub
  - [ ] Create basic REST controllers
- [ ] **Configuration**
  - [ ] Set up appsettings.json
  - [ ] Create docker-compose.yml for MongoDB + Redis
  - [ ] Create .env.example

### Phase 2: Core Features (Medium Priority)

- [ ] **Channels & Threads**
  - [ ] Channel CRUD operations
  - [ ] Thread support within channels
  - [ ] Channel membership management
- [ ] **Messaging**
  - [ ] Message persistence to MongoDB
  - [ ] Message history retrieval
  - [ ] Real-time message broadcasting
  - [ ] Message editing and deletion
- [ ] **Reactions**
  - [ ] Add/remove reactions on messages
  - [ ] Reaction count aggregation
- [ ] **File Sharing**
  - [ ] File upload endpoint
  - [ ] File storage service
  - [ ] File attachment to messages
- [ ] **Blazor Client**
  - [ ] Chat page with real-time updates
  - [ ] Channel sidebar navigation
  - [ ] Message input component
  - [ ] Reaction picker component
  - [ ] File upload component

### Phase 3: Polish & Optimization (Lower Priority)

- [ ] **Sentiment Analysis**
  - [ ] Train ML.NET model
  - [ ] Integrate sentiment analysis service
  - [ ] Display mood indicators in UI
- [ ] **Presence System**
  - [ ] Online/offline status
  - [ ] Typing indicators
  - [ ] Last seen timestamps
- [ ] **Authentication**
  - [ ] JWT authentication
  - [ ] User registration/login
  - [ ] SignalR authentication
- [ ] **Testing**
  - [ ] Unit tests for repositories
  - [ ] Unit tests for services
  - [ ] Integration tests for hubs
  - [ ] Integration tests for API endpoints
- [ ] **CI/CD**
  - [ ] GitHub Actions CI pipeline
  - [ ] Azure deployment pipeline
  - [ ] Docker containerization
- [ ] **Performance**
  - [ ] Message pagination
  - [ ] Redis caching for hot data
  - [ ] Connection pooling optimization

---

## 3. Milestones

| Milestone | Description | Target |
|-----------|-------------|--------|
| M1 | Project structure + basic SignalR communication | Week 1 |
| M2 | MongoDB persistence + channels | Week 2 |
| M3 | Threads, reactions, file sharing | Week 3 |
| M4 | Blazor client MVP | Week 4 |
| M5 | Sentiment analysis + presence | Week 5 |
| M6 | Testing + CI/CD + deployment | Week 6 |

---

## 4. Dependencies

### External Services

| Service | Purpose | Local Dev | Production |
|---------|---------|-----------|------------|
| MongoDB | Message/channel persistence | Docker | MongoDB Atlas |
| Redis | Backplane + presence + pub/sub | Docker | Azure Cache for Redis |
| Azure SignalR | Managed SignalR service | Local SignalR | Azure SignalR Service |
| Azure Blob Storage | File storage | Local folder | Azure Blob |

### NuGet Packages

| Package | Project | Purpose |
|---------|---------|---------|
| MongoDB.Driver | Infrastructure | MongoDB access |
| StackExchange.Redis | Infrastructure | Redis client |
| Microsoft.ML | Infrastructure | Sentiment analysis |
| Microsoft.AspNetCore.SignalR.StackExchangeRedis | API | Redis backplane |
| Microsoft.AspNetCore.SignalR.Client | Client | Blazor SignalR client |
| Moq | Tests | Mocking |
| FluentAssertions | Tests | Assertion library |
| Microsoft.AspNetCore.Mvc.Testing | Tests | Integration testing |
