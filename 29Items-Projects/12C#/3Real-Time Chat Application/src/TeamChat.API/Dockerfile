FROM mcr.microsoft.com/dotnet/aspnet:9.0 AS base
WORKDIR /app
EXPOSE 80
EXPOSE 443

FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
WORKDIR /src

# Copy project files
COPY ["src/TeamChat.API/TeamChat.API.csproj", "TeamChat.API/"]
COPY ["src/TeamChat.Core/TeamChat.Core.csproj", "TeamChat.Core/"]
COPY ["src/TeamChat.Infrastructure/TeamChat.Infrastructure.csproj", "TeamChat.Infrastructure/"]

# Restore dependencies
RUN dotnet restore "TeamChat.API/TeamChat.API.csproj"

# Copy source code
COPY src/ .

# Build
WORKDIR "/src/TeamChat.API"
RUN dotnet build "TeamChat.API.csproj" -c Release -o /app/build

FROM build AS publish
RUN dotnet publish "TeamChat.API.csproj" -c Release -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .

# Create uploads directory
RUN mkdir -p /app/uploads

ENTRYPOINT ["dotnet", "TeamChat.API.dll"]
